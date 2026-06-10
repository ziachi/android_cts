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

package android.ondeviceintelligence.cts;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_ENABLE_ON_DEVICE_INTELLIGENCE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;

import android.Manifest;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
public class OnDeviceIntelligenceServiceTest {
    private static final String TAG = "OnDeviceIntelligenceServiceTest";
    private Context mContext;
    private String sanboxedServiceComponentName;
    private String intelligenceServiceComponentName;

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        sanboxedServiceComponentName =
                mContext.getResources()
                        .getString(
                                mContext.getResources()
                                        .getIdentifier(
                                                "config_defaultOnDeviceSandboxedInferenceService",
                                                "string",
                                                "android"));
        intelligenceServiceComponentName =
                mContext.getResources()
                        .getString(
                                mContext.getResources()
                                        .getIdentifier(
                                                "config_defaultOnDeviceIntelligenceService",
                                                "string",
                                                "android"));
    }

    @Test
    public void bothServicesShouldBeConfiguredIfEitherIsNotEmpty() {
        assumeFalse("Services not configured.",
                TextUtils.isEmpty(sanboxedServiceComponentName) && TextUtils.isEmpty(
                        intelligenceServiceComponentName));

        if (!TextUtils.isEmpty(sanboxedServiceComponentName) || !TextUtils.isEmpty(
                intelligenceServiceComponentName)) {
            assertFalse(TextUtils.isEmpty(sanboxedServiceComponentName));
            assertFalse(TextUtils.isEmpty(intelligenceServiceComponentName));
        }
    }

    @Test
    public void sandboxedServiceConfiguredShouldBeIsolated() throws Exception {
        assumeFalse("Service not configured.", TextUtils.isEmpty(sanboxedServiceComponentName));
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            validateSandboxedService(sanboxedServiceComponentName);
        } catch (PackageManager.NameNotFoundException e) {
            assumeNoException("Service not present in the device.", e);
        }
    }

    private void validateSandboxedService(String serviceName)
            throws PackageManager.NameNotFoundException {
        ComponentName serviceComponent = ComponentName.unflattenFromString(
                serviceName);
        Context systemUserContext = mContext.createContextAsUser(UserHandle.SYSTEM, 0);
        ServiceInfo serviceInfo = systemUserContext.getPackageManager().getServiceInfo(
                serviceComponent,
                PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        assertNotNull("Remote service is not configured to complete the request.", serviceInfo);
        final String permission = serviceInfo.permission;
        String requiredPermission =
                Manifest.permission.BIND_ON_DEVICE_SANDBOXED_INFERENCE_SERVICE;
        assertEquals(String.format(
                "Service %s requires %s permission. Found %s permission",
                serviceInfo.getComponentName(),
                requiredPermission,
                serviceInfo.permission), permission, requiredPermission);
        assertTrue("Configuration required an isolated service, but the configured "
                        + "service: " + serviceName + ", is not isolated",
                isIsolatedService(serviceInfo));
    }

    private static boolean isIsolatedService(@NonNull ServiceInfo serviceInfo) {
        return (serviceInfo.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0
                && (serviceInfo.flags & ServiceInfo.FLAG_EXTERNAL_SERVICE) == 0;
    }
}
