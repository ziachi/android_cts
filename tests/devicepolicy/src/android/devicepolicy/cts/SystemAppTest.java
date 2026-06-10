/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;

import static org.testng.Assert.assertThrows;

import android.stats.devicepolicy.EventId;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.EnableSystemApp;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SystemAppTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = testApps(sDeviceState).any();
    private static TestAppInstance sTestAppInstance;

    private static final Package SYSTEM_APP =
            TestApis.packages().systemApps().stream().findFirst().get();

    @BeforeClass
    public static void setUpClass() {
        sTestAppInstance = sTestApp.install();
    }

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

    @CanSetPolicyTest(policy = EnableSystemApp.class)
    @Postsubmit(reason = "new test")
    public void enableSystemApp_nonSystemApp_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> dpc(sDeviceState).devicePolicyManager().enableSystemApp(
                        dpc(sDeviceState).componentName(), sTestApp.packageName()));
    }

    @CannotSetPolicyTest(policy = EnableSystemApp.class)
    @Postsubmit(reason = "new test")
    public void enableSystemApp_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().enableSystemApp(
                        dpc(sDeviceState).componentName(), sTestApp.packageName()));
    }

    @CanSetPolicyTest(policy = EnableSystemApp.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#enableSystemApp"})
    public void enableSystemApp_isLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {

            dpc(sDeviceState).devicePolicyManager().enableSystemApp(
                    dpc(sDeviceState).componentName(), SYSTEM_APP.packageName());

            assertThat(metrics.query()
                    .whereType()
                    .isEqualTo(EventId.ENABLE_SYSTEM_APP_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isEqualTo(dpc(sDeviceState).isDelegate())
                    .whereStrings().contains(SYSTEM_APP.packageName())
            ).wasLogged();
        }
    }
}