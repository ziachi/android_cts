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
package android.hardware.multiprocess.camera.cts;

import static junit.framework.Assert.*;

import android.app.ActivityManager;
import android.util.ArrayMap;

import java.util.List;
import java.util.Map;

/**
 * Utility functions used throughout the multi-process unit tests.
 */
public class TestUtils {
    /**
     * Return the PID for the process with the given name in the given list of process info.
     *
     * @param processName the name of the process who's PID to return.
     * @param list a list of {@link ActivityManager.RunningAppProcessInfo} to check.
     * @return the PID of the given process, or -1 if it was not included in the list.
     */
    public static int getPid(
            String processName, List<ActivityManager.RunningAppProcessInfo> list) {
        for (ActivityManager.RunningAppProcessInfo rai : list) {
            if (processName.equals(rai.processName)) return rai.pid;
        }
        return -1;
    }

    /**
     * Assert that there is only one event of the given type in the event list.
     *
     * @param event event type to check for.
     * @param events {@link List} of events.
     */
    public static void assertOnly(int event, List<ErrorLoggingService.LogEvent> events) {
        assertTrue("Remote camera activity never received event: " + event, events != null);
        for (ErrorLoggingService.LogEvent e : events) {
            assertFalse(
                    "Remote camera activity received invalid event ("
                            + e
                            + ") while waiting for event: "
                            + event,
                    e.getEvent() < 0 || e.getEvent() != event);
        }
        assertTrue("Remote camera activity never received event: " + event, events.size() >= 1);
        assertTrue(
                "Remote camera activity received too many "
                        + event
                        + " events, received: "
                        + events.size(),
                events.size() == 1);
    }

    /**
     * Assert there were no logEvents in the given list.
     *
     * @param msg message to show on assertion failure.
     * @param events {@link List} of events.
     */
    public static void assertNone(String msg, List<ErrorLoggingService.LogEvent> events) {
        if (events == null) return;
        StringBuilder builder = new StringBuilder(msg + "\n");
        for (ErrorLoggingService.LogEvent e : events) {
            builder.append(e).append("\n");
        }
        assertTrue(builder.toString(), events.isEmpty());
    }

    /** Return a Map of eventTag -> number of times encountered */
    public static Map<Integer, Integer> getEventTagCountMap(
            List<ErrorLoggingService.LogEvent> events) {
        ArrayMap<Integer, Integer> eventTagCountMap = new ArrayMap<>();
        for (ErrorLoggingService.LogEvent e : events) {
            int eventTag = e.getEvent();
            if (!eventTagCountMap.containsKey(eventTag)) {
                eventTagCountMap.put(eventTag, 1);
            } else {
                eventTagCountMap.put(eventTag, eventTagCountMap.get(eventTag) + 1);
            }
        }
        return eventTagCountMap;
    }
}
