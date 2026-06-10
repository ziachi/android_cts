/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.stubs;

import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.app.Flags;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import java.util.List;

public class StubRemoteService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.d("Process test stub", "StubRemoteServiceProcessPid:" + Process.myPid());
    }

    private final ISecondary.Stub mSecondaryBinder = new ISecondary.Stub() {
        public int getPid() {
            return Process.myPid();
        }

        public long getElapsedCpuTime() {
            return Process.getElapsedCpuTime();
        }

        public String getTimeZoneID() {
            return java.util.TimeZone.getDefault().getID();
        }

        /**
         * Checks the ApplicationStartInfo to see if the app had been force-stopped earlier
         * and returns the start reason. Returns -1 if the app was not in a stopped state.
         * @return the start reason, if previously stopped, -1 otherwise.
         */
        public int getWasForceStoppedReason() {
            if (Flags.appStartInfo()) {
                List<ApplicationStartInfo> startReasons =
                        getSystemService(ActivityManager.class).getHistoricalProcessStartReasons(1);
                if (startReasons != null && !startReasons.isEmpty()) {
                    ApplicationStartInfo asi = startReasons.get(0);
                    if (asi.wasForceStopped()) {
                        return asi.getReason();
                    }
                }
            }
            return -1; // Wasn't force-stopped or don't have ApplicationStartInfo
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        final String action = intent.getAction();
        if (action != null
                && action.startsWith(ISecondary.class.getName())) {
            return mSecondaryBinder;
        }
        return null;
    }
}
