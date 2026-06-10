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
package android.edi.cts;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;

public class KernelDeviceInfo extends DeviceInfo {

    private static final String BUILD_SYSTEM = "build_system";

    /**
     * This provides the device's current boot state, which represents the level of protection
     * provided to the user and to apps after the device finishes booting.
     */
    public enum KernelBuildSystem {
        KERNEL_BUILD_SYSTEM_UNSPECIFIED,
        KERNEL_BUILD_SYSTEM_UNKNOWN,
        KERNEL_BUILD_SYSTEM_KLEAF
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        ITestDevice device = getDevice();

        CommandResult commandResult = device.executeShellV2Command("cat /proc/version");
        if (commandResult.getExitCode() != 0) {
            CLog.e("Impossible to run `cat /proc/version`");
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNSPECIFIED.name());
            return;
        }

        String output = commandResult.getStdout();
        if (output == null) {
            CLog.e("Empty output");
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNSPECIFIED.name());
            return;
        }

        output = output.trim();
        if (output.isEmpty()) {
            CLog.e("Empty output");
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNSPECIFIED.name());
            return;
        }

        if (output.contains("(kleaf@build-host)")) {
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_KLEAF.name());
        } else {
            store.addResult(BUILD_SYSTEM, KernelBuildSystem.KERNEL_BUILD_SYSTEM_UNKNOWN.name());
        }
    }
}
