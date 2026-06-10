/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BindUtils {
    private static final String TAG = BindUtils.class.getSimpleName();
    private static final Map<TelecomTestApp, Pair<TelecomAppServiceConnection, AppControlWrapper>>
            sTelecomAppToService = new HashMap<>();

    public static boolean hasBoundTestApp() {
        // If the sTelecomAppToService is NOT empty, that means that an app has not unbound yet
        return !sTelecomAppToService.isEmpty();
    }

    public static void printBoundTestApps() {
        for (TelecomTestApp testAppName : sTelecomAppToService.keySet()) {
            Log.i(TAG, String.format("printBoundTestApps: [%s] is currently bound", testAppName));

        }
    }

    private static class TelecomAppServiceConnection implements ServiceConnection {
        private static final String TAG = TelecomAppServiceConnection.class.getSimpleName();
        private final CompletableFuture<IAppControl> mFuture;

        TelecomAppServiceConnection(CompletableFuture<IAppControl> future) {
            mFuture = future;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, String.format("onServiceConnected: ComponentName=[%s]", name));
            mFuture.complete(IAppControl.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, String.format("onServiceDisconnected: ComponentName=[%s]", name));
            mFuture.complete(null);
        }
    }

    /**
     * This helper method handles the binding process for a given test app.  It ensures the
     * that the test app signals that onBind is finished executing before returning control to the
     * test.
     */
    public AppControlWrapper bindToApp(Context context, TelecomTestApp appName)
            throws Exception {
        // if a test app is already bound, return the existing binding
        if (sTelecomAppToService.containsKey(appName)) {
            return sTelecomAppToService.get(appName).second;
        }
        final AppControlWrapper appControl = waitOnBindForApp(context, appName);
        // For debugging purposes, it is good to know how much time for an app to signal it bound!
        Log.i(TAG, String.format("bindToApp: wait for %s to signal onBind is complete ", appName));
        long startTimeMillis = SystemClock.elapsedRealtime();
        WaitUntil.waitUntilConditionIsTrueOrTimeout(new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return appControl.isBound();
                    }
                },
                WaitUntil.DEFAULT_TIMEOUT_MS,
                "Timed out waiting for isBound to return <TRUE> for [" + appName + "]");
        long elapsed = SystemClock.elapsedRealtime() - startTimeMillis;
        Log.i(TAG, String.format("bindToApp: %s took %d milliseconds signal it was bound",
                appName, elapsed));
        return appControl;
    }

    /**
     * This helper method handles the unbinding process for a given test app.  It ensures the
     * that the test app signals that onUnbind is finished executing before returning control to the
     * test.
     */
    public void unbindFromApp(Context context, AppControlWrapper appControl) {
        TelecomTestApp name = appControl.getTelecomApps();
        Log.i(TAG, String.format("unbindFromApplication: applicationName=[%s]", name));
        if (!sTelecomAppToService.containsKey(name)) {
            Log.i(TAG, String.format("cannot find the service binder for application=[%s]", name));
            return;
        }
        try {
            TelecomAppServiceConnection serviceConnection = sTelecomAppToService.get(name).first;
            context.unbindService(serviceConnection);
            long startTimeMillis = SystemClock.elapsedRealtime();
            Log.i(TAG, String.format("unbindFromApp: wait for %s to signal onUnbind is complete ",
                    name));
            WaitUntil.waitUntilConditionIsTrueOrTimeout(new Condition() {
                        @Override
                        public Object expected() {
                            return false;
                        }

                        @Override
                        public Object actual() {
                            return appControl.isBound();
                        }
                    },
                    WaitUntil.DEFAULT_TIMEOUT_MS,
                    "Timed out waiting for isBound to return <FALSE>");
            long elapsedMs = SystemClock.elapsedRealtime() - startTimeMillis;
            Log.i(TAG, String.format("unbindFromApp: %s took %d milliseconds to signal it was "
                    + "unbound", name, elapsedMs));
        } catch (Exception e) {
            // Note: Do not throw the UnBind Exception! Otherwise, the test will fail with an unbind
            // error instead of a potential underlying cause.
            Log.e(TAG, String.format("unbindFromApplication: app=[%s], e=[%s]", name, e));
        }
        finally {
            sTelecomAppToService.remove(name);
        }
    }

    private Intent createBindIntentForApplication(TelecomTestApp application) throws Exception {
        Intent bindIntent = new Intent(getBindActionFromApplicationName(application));
        bindIntent.setPackage(getPackageNameFromApplicationName(application));
        return bindIntent;
    }

    private String getBindActionFromApplicationName(TelecomTestApp app) throws Exception {
        switch (app) {
            case TransactionalVoipAppMain, TransactionalVoipAppClone -> {
                return TelecomTestApp.T_CONTROL_INTERFACE_ACTION;
            }
            case ConnectionServiceVoipAppMain, ConnectionServiceVoipAppClone -> {
                return TelecomTestApp.VOIP_CS_CONTROL_INTERFACE_ACTION;
            }
            case ManagedConnectionServiceApp, ManagedConnectionServiceAppClone -> {
                return TelecomTestApp.CONTROL_INTERFACE_ACTION;
            }
        }
        throw new Exception(
                String.format("%s doesn't have a <CONTROL_INTERFACE> mapping." + app));
    }

    private static String getPackageNameFromApplicationName(TelecomTestApp app) throws Exception {
        switch (app) {
            case TransactionalVoipAppMain -> {
                return TelecomTestApp.TRANSACTIONAL_PACKAGE_NAME;
            }
            case TransactionalVoipAppClone -> {
                return TelecomTestApp.TRANSACTIONAL_CLONE_PACKAGE_NAME;
            }
            case ConnectionServiceVoipAppMain -> {
                return TelecomTestApp.SELF_MANAGED_CS_MAIN_PACKAGE_NAME;
            }
            case ConnectionServiceVoipAppClone -> {
                return TelecomTestApp.SELF_MANAGED_CS_CLONE_PACKAGE_NAME;
            }
            case ManagedConnectionServiceApp -> {
                return TelecomTestApp.MANAGED_PACKAGE_NAME;
            }
            case ManagedConnectionServiceAppClone -> {
                return TelecomTestApp.MANAGED_CLONE_PACKAGE_NAME;
            }
        }
        throw new Exception(
                String.format("%s doesn't have a <PACKAGE_NAME> mapping.", app));
    }

    private AppControlWrapper waitOnBindForApp(Context context, TelecomTestApp appName)
            throws Exception {
        TelecomAppServiceConnection serviceConnection;
        if (sTelecomAppToService.containsKey(appName)) {
            Log.i(TAG, String.format("returning cached service binder for application=[%s]",
                    appName));
            return sTelecomAppToService.get(appName).second;
        } else {
            CompletableFuture<IAppControl> f = new CompletableFuture<>();
            serviceConnection = new TelecomAppServiceConnection(f);
            Log.i(TAG, String.format("waitOnBindForApp: requesting bind to %s", appName));
            long startTimeMillis = SystemClock.elapsedRealtime();
            boolean success = context.bindService(createBindIntentForApplication(appName),
                    serviceConnection, Context.BIND_AUTO_CREATE);
            long elapsedMs = SystemClock.elapsedRealtime() - startTimeMillis;
            Log.i(TAG, String.format("waitOnBindForApp: finished bind to %s in %d milliseconds",
                    appName, elapsedMs));
            if (!success) {
                fail("Failed to get control interface -- bind error");
            }
            IAppControl iAppControl = f.get(WaitUntil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            AppControlWrapper wrapper = new AppControlWrapper(iAppControl, appName);
            sTelecomAppToService.put(appName, new Pair<>(serviceConnection, wrapper));
            return wrapper;
        }
    }
}
