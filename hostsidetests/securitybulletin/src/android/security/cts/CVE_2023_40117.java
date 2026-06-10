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

package android.security.cts;

import static com.android.sts.common.CommandUtil.runAndCheck;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_40117 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 253043065)
    @Test
    public void testPocCVE_2023_40117() {
        try {
            ITestDevice device = getDevice();
            final String global = "global";
            final String secure = "secure";
            final String frpModeName = "secure_frp_mode";
            final String enableFRPMode = "1";

            // Creating maps for settings namespaces and reset modes
            List<String> namespaces = Arrays.asList(global, secure);
            List<String> resetModes =
                    Arrays.asList("trusted_defaults", "untrusted_defaults", "untrusted_clear");

            try (
                // Reset and restore all settings
                AutoCloseable withPreservedSettings = withPreservedSettings(device, namespaces);
                    AutoCloseable withEnabledFRPGlobal =
                            withSetting(device, global, frpModeName, enableFRPMode);
                    AutoCloseable withEnabledFRPSecure =
                            withSetting(device, secure, frpModeName, enableFRPMode);
                ) {

                // Reset all the settings for all namespaces
                for (String namespace : namespaces) {
                    for (String resetMode : resetModes) {
                        runAndCheck(
                                device,
                                String.format("settings reset %s %s", namespace, resetMode));
                    }
                }

                // Fetch the current states of 'secure_frp_mode' for both 'secure' and 'global'
                String secureFRPModeGlobal = device.getSetting(global, frpModeName);
                String secureFRPModeSecure = device.getSetting(secure, frpModeName);

                // Without fix 'SECURE_FRP_MODE' gets reset
                // With fix 'SECURE_FRP_MODE' stays enabled
                assertWithMessage(
                                "Device is vulnerable to b/253043065 !! secure_frp_mode should be"
                                        + "excluded from resets!")
                        .that(
                                (secureFRPModeGlobal != null
                                                && secureFRPModeGlobal.equals(enableFRPMode))
                                        && secureFRPModeSecure != null
                                        && secureFRPModeSecure.equals(enableFRPMode))
                        .isTrue();
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private AutoCloseable withPreservedSettings(ITestDevice device, List<String> namespaces)
            throws Exception {
        // Save all settings
        Map<String, Map<String, String>> allSettingsOld =
                new HashMap<String, Map<String, String>>();
        for (String namespace : namespaces) {
            Map<String, String> namespaceSettings = device.getAllSettings(namespace);
            allSettingsOld.put(namespace, namespaceSettings);
        }

        // Returning AutoCloseable to restore all the settings
        return () -> {
            for (String namespace : namespaces) {
                Map<String, String> settingsWithValue = allSettingsOld.get(namespace);
                for (Map.Entry<String, String> entry : settingsWithValue.entrySet()) {
                    device.setSetting(namespace, entry.getKey(), entry.getValue());
                }
            }
        };
    }
}
