/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System native device info collector.
 */
public class NativeDeviceInfo extends DeviceInfo {

    private void collectMemCG(ITestDevice device, HostInfoStore store) throws Exception {
        CommandResult commandResult = device.executeShellV2Command("grep memory /proc/cgroups");

        store.startGroup("memcg");
        if (commandResult.getExitCode() == 0) {
            String[] tokens = commandResult.getStdout().split("\\s+");
            boolean memcg_enabled = tokens[3].equals("1");
            store.addResult("enabled", memcg_enabled);
            if (memcg_enabled) store.addResult("version", tokens[1].equals("0") ? "2" : "1");
        } else if (commandResult.getExitCode() == 1) { // "memory" not found by grep
            store.addResult("version", -3);
        } else if (commandResult.getStderr().contains("No such file")) {
            store.addResult("version", -1);
        } else if (commandResult.getStderr().contains("Permission denied")) {
            store.addResult("version", -2);
        }
        store.endGroup();
    }

    private void collectMGLRU(ITestDevice device, HostInfoStore store) throws Exception {
        CommandResult commandResult = device.executeShellV2Command(
                "cat /sys/kernel/mm/lru_gen/enabled");

        if (commandResult.getExitCode() == 0) {
            store.addResult("mglru_enabled", Integer.decode(commandResult.getStdout().trim()));
        } else if (commandResult.getStderr().contains("No such file")) {
            store.addResult("mglru_enabled", -1);
        } else if (commandResult.getStderr().contains("Permission denied")) {
            store.addResult("mglru_enabled", -2);
        }
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        ITestDevice device = getDevice();
        CommandResult commandResult = device.executeShellV2Command("cat /proc/self/maps");
        if (!commandResult.getStderr().isEmpty()) {
            CLog.w("Warnings occurred when running cat:\n%s", commandResult.getStderr());
        }
        if (commandResult.getExitCode() == null) {
            throw new NullPointerException("cat command exit code is null");
        }
        if (commandResult.getExitCode() != 0) {
            throw new IllegalStateException(
                String.format("cat commaned returned %d: %s", commandResult.getExitCode(),
                              commandResult.getStderr()));
        }
        String stdout = commandResult.getStdout();
        if (stdout == null) {
            throw new NullPointerException("cat command resulted in no output");
        }

        String allocatorName;
        if (stdout.indexOf(":scudo:") != -1) {
            allocatorName = "scudo";
        } else {
            allocatorName = "jemalloc";
        }

        // Check for the bitness of the device. A device that supports
        // both 32 bits and 64 bits is assumed to be whatever the shell
        // user runs as.
        // On 32 bit devices, the format of the entries is:
        //   beace000-beaf0000 rw-p 00000000 00:00 0          [stack]
        // On 64 bit devices, the format of the entries is:
        //   7ffdc13000-7ffdc35000 rw-p 00000000 00:00 0      [stack]
        // This pattern looks for an entry that contains only 8 hex digits
        // in the first and second map address to indicate 32 bit devices.
        Pattern mapPattern = Pattern.compile("^[0-9a-f]{8,8}-[0-9a-f]{8,8} ");
        Matcher matcher = mapPattern.matcher(stdout);
        if (matcher.find()) {
            allocatorName += "32";
        } else {
            allocatorName += "64";
        }

        if (device.getBooleanProperty("ro.config.low_ram", false)) {
            allocatorName += "_lowmemory";
        }
        store.addResult("allocator", allocatorName);

        collectMemCG(device, store);
        collectMGLRU(device, store);
        collectSuspendMechanism(device, store);
    }

    private void collectSuspendMechanism(ITestDevice device, HostInfoStore store) throws Exception {
        CommandResult commandResult = device.executeShellV2Command("cat /sys/power/mem_sleep");

        if (commandResult.getExitCode() == 0) {
            String memSleepOutput = commandResult.getStdout().trim();
            // Assuming the output format is like "[s2idle] deep".
            Pattern pattern = Pattern.compile("\\[(.*?)\\]");
            Matcher matcher = pattern.matcher(memSleepOutput);
            if (matcher.find()) {
                String suspendMechanism = matcher.group(1);
                store.addResult("suspend_mechanism", suspendMechanism);
            } else {
                // Handle cases where the output format is unexpected
                store.addResult("suspend_mechanism", "unknown");
            }
        } else if (commandResult.getStderr().contains("No such file")) {
            store.addResult("suspend_mechanism", "error: No such file");
        } else if (commandResult.getStderr().contains("Permission denied")) {
            store.addResult("suspend_mechanism", "error: Permission denied");
        }
    }
}
