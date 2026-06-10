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

package android.telecom.cts.apps;

import android.telecom.Connection;

import java.util.ArrayList;
import java.util.List;

/* Mimics the HoldTracker class used by Telephony to update the holdability of managed connection
 * for the purpose of CUJ testing. This is simplified class that only applies to connections. */
public class HoldableTracker {
    private static final List<Connection> sTrackedConnections = new ArrayList<>();

    public static void addHoldable(Connection conn) {
        sTrackedConnections.add(conn);
        updateHoldCapability();
    }

    public static void removeHoldable(Connection conn) {
        sTrackedConnections.remove(conn);
        updateHoldCapability();
    }

    private static void updateHoldCapability() {
        boolean isHoldable = sTrackedConnections.size() < 2;
        int mask = (1 << 31) - 1;
        int clearHoldMask = (~Connection.CAPABILITY_HOLD) & mask;
        for (Connection conn : sTrackedConnections) {
            int finalCaps =
                    isHoldable
                            ? conn.getConnectionCapabilities() | Connection.CAPABILITY_HOLD
                            : conn.getConnectionCapabilities() & clearHoldMask;
            conn.setConnectionCapabilities(finalCaps);
        }
    }

    public static void clearTrackedConnections() {
        sTrackedConnections.clear();
    }

    public static boolean canHold() {
        return sTrackedConnections.size() < 2;
    }
}
