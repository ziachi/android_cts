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

import java.util.Set;

public class ServiceDeviceInfo extends DeviceInfo {
    // Add new binder services to be monitored in the following Set
    private static final Set<String> MONITORED_SERVICES = Set.of("tradeinmode");

    private static final String PACKAGE = "binder_services";
    private static final String NAME = "name";
    private static final String INTERFACE_DESCRIPTOR = "interface_descriptor";
    private static final String AVAILABILITY = "is_available";

    private static class ServiceEntry {
        public String name;
        public String interfaceDescriptor;
    }

    protected ServiceEntry parseServicesLine(HostInfoStore store, String line) throws Exception {
        // The first line begins returns the number of services found and can
        // be skipped. It's the only line string with "Found".
        if (line.startsWith("Found")) {
            return null;
        }

        // Each line has the format:
        // "<int:line number> <str:service name>: [<interface descriptor>]
        String[] entries = line.split("\\s+");
        if (entries.length != 3) {
            return null;
        }

        // Remove the last character of the string as it corresponds to a colon.
        if (entries[1].length() == 0) {
            return null;
        }

        ServiceEntry se = new ServiceEntry();

        se.name = entries[1].substring(0, entries[1].length() - 1);

        // Remove the first and last characters of the string as they correspond
        // to square brackets.
        if (entries.length > 2 && entries[2].length() > 2) {
            se.interfaceDescriptor = entries[2].substring(1, entries[2].length() - 1);
        }

        return se;
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        ITestDevice device = getDevice();

        CommandResult commandResult = device.executeShellV2Command("service list");
        if (commandResult.getExitCode() != 0) {
            CLog.e("The 'service' command exited with error");
            return;
        }

        String output = commandResult.getStdout();
        if (output == null) {
            CLog.e("Empty output");
            return;
        }

        output = output.trim();
        if (output.isEmpty()) {
            CLog.e("Empty output");
            return;
        }

        store.startArray(PACKAGE);

        for (String name : MONITORED_SERVICES) {
            ServiceEntry se = null;
            boolean found = false;

            // Iterate over every line of the output, each line corresponding to a
            // service found in the device.
            for (String line : output.split("\\r?\\n")) {
                se = parseServicesLine(store, line);
                if (se != null && name.equals(se.name)) {
                    found = true;
                    break;
                }
            }
            store.startGroup();
            store.addResult(NAME, name);
            store.addResult(INTERFACE_DESCRIPTOR, found ? se.interfaceDescriptor : "");
            store.addResult(AVAILABILITY, found);
            store.endGroup();
        }

        store.endArray();
    }
}
