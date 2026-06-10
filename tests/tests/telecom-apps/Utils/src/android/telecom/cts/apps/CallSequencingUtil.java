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

package android.telecom.cts.apps;

import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceAppClone;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

public class CallSequencingUtil {
    public static final int INCOMING_MANAGED_CALL = 0;
    public static final int OUTGOING_MANAGED_CALL = 1;
    public static final int INCOMING_SELF_MANAGED_CALL = 2;
    public static final int OUTGOING_SELF_MANAGED_CALL = 3;
    public static final int INCOMING_TRANSACTIONAL_CALL = 4;
    public static final int OUTGOING_TRANSACTIONAL_CALL = 5;
    public static final int[] VALID_COMBINATIONS = new int[] {INCOMING_TRANSACTIONAL_CALL,
            OUTGOING_TRANSACTIONAL_CALL, INCOMING_SELF_MANAGED_CALL, OUTGOING_SELF_MANAGED_CALL,
            INCOMING_MANAGED_CALL, OUTGOING_MANAGED_CALL};

    public static final String[] CALL_TYPE_NAME = new String[] {"IMC", "OMC", "ISM", "OSM", "ITC",
            "OTC"};

    public static boolean isOutgoing(int callType) {
        switch (callType) {
            case INCOMING_MANAGED_CALL, INCOMING_SELF_MANAGED_CALL, INCOMING_TRANSACTIONAL_CALL -> {
                return false;
            }
            case OUTGOING_MANAGED_CALL, OUTGOING_SELF_MANAGED_CALL, OUTGOING_TRANSACTIONAL_CALL -> {
                return true;
            }
            default -> throw new IllegalArgumentException("isOutgoing: unknown callType="
                    + callType);
        }
    }

    public static TelecomTestApp getTelecomTestApp(int callType) {
        switch (callType) {
            // NOTE: Managed calls use the same ConnectionService impl right now - to support
            // different combinations will require a test improvement
            case INCOMING_MANAGED_CALL -> {
                return ManagedConnectionServiceApp;
            }
            case OUTGOING_MANAGED_CALL -> {
                return ManagedConnectionServiceAppClone;
            }
            case INCOMING_TRANSACTIONAL_CALL -> {
                return TransactionalVoipAppMain;
            }
            case OUTGOING_TRANSACTIONAL_CALL -> {
                return TransactionalVoipAppClone;
            }
            case INCOMING_SELF_MANAGED_CALL -> {
                return ConnectionServiceVoipAppMain;
            }
            case OUTGOING_SELF_MANAGED_CALL -> {
                return ConnectionServiceVoipAppClone;
            }
            default -> {
                return null;
            }
        }
    }
}
