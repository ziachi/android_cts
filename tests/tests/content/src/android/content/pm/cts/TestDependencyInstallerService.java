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

package android.content.pm.cts;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.dependencyinstaller.DependencyInstallerCallback;
import android.content.pm.dependencyinstaller.DependencyInstallerService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.PackageUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import libcore.util.HexEncoding;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * A DependencyInstallerService for test.
 *
 * The behavior of the service is controlled by the test by passing specific method names via
 * SharedPreferences.
 *
 */
public class TestDependencyInstallerService extends DependencyInstallerService {

    private static final String TAG = TestDependencyInstallerService.class.getSimpleName();
    private static final String LIB_NAME_SDK_1 = "com.test.sdk1";
    private static final String LIB_NAME_SDK_2 = "com.test.sdk2";
    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_SDK1_APK_NAME = "HelloWorldSdk1";
    private static final String TEST_SDK2_APK_NAME = "HelloWorldSdk2";
    private static final int WAIT_FOR_INSTALL_MS = 60 * 1000;

    static final String METHOD_NAME = TAG + "-method-name";
    static final String ERROR_MESSAGE = TAG + "-error-message";
    static final String METHOD_INSTALL_SYNC = TAG + "-install-sync";
    static final String METHOD_INSTALL_ASYNC = TAG + "-install-async";
    static final String METHOD_INVALID_SESSION_ID = TAG + "-invalid-session-id";
    static final String METHOD_ABANDONED_SESSION_ID = TAG + "-abandoned-session-id";
    static final String METHOD_ABANDON_SESSION_DURING_INSTALL = TAG
            + "-abandon-session-during-install";
    static final String METHOD_RESUME_ON_FAILURE_FAIL_INSTALL = TAG
            + "-resume-on-failure-fail-install";
    static final String METHOD_VERIFY_USER_ID = TAG + "-verify-user-id";

    private String mCertDigest;

    @Override
    public void onDependenciesRequired(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback) {

        String methodName = getMethodName();
        int userId = UserHandle.myUserId();
        Log.d(TAG, "onDependenciesRequired call received: " + methodName + " for user: " + userId);

        try {

            if (!isInstrumented()) {
                // Test app is not instrumented when we bind to it on a different user.

                // For multi-user test, methodName is always empty since it's written in
                // SharedPreferences storage of a different user. So we opt to install synchronously
                // all the time.
                installDependenciesSync(neededLibraries, callback);
                return;
            }

            // All CTS test artifacts are signed with same cert. So we can assume the SDK apks
            // we have will be same cert as our current package.
            mCertDigest = getPackageCertDigest(getContext().getPackageName());
            validateNeededLibraries(neededLibraries);

            if (methodName.equals(METHOD_INSTALL_SYNC)) {
                installDependenciesSync(neededLibraries, callback);
            } else if (methodName.equals(METHOD_INSTALL_ASYNC)) {
                installDependenciesAsync(neededLibraries, callback);
            } else if (methodName.equals(METHOD_INVALID_SESSION_ID)) {
                testInvalidSessionId(callback);
            } else if (methodName.equals(METHOD_ABANDONED_SESSION_ID)) {
                testAbandonedSessionId(callback);
            } else if (methodName.equals(METHOD_ABANDON_SESSION_DURING_INSTALL)) {
                testAbandonSessionDuringInstall(neededLibraries, callback);
            } else if (methodName.equals(METHOD_VERIFY_USER_ID)) {
                installDependenciesSync(neededLibraries, callback);
            } else if (methodName.equals(METHOD_RESUME_ON_FAILURE_FAIL_INSTALL)) {
                testResumeOnFailureFailsInstall(neededLibraries, callback);
            } else {
                throw new IllegalStateException("Unknown method name: " + methodName);
            }
        } catch (Throwable e) {
            Log.w(TAG, e.getMessage(), e);
            setErrorMessage(e.getMessage());
            try {
                callback.onFailureToResolveAllDependencies();
            } catch (Exception e2) {
                Log.w(TAG, e2.getMessage());
            }
        }
    }

    private boolean isSdk1(SharedLibraryInfo info) {
        if (info.getCertDigests().isEmpty() || info.getName() == null) {
            return false;
        }
        return info.getName().equals(LIB_NAME_SDK_1)
            && info.getCertDigests().get(0).equals(mCertDigest);
    }

    private boolean isSdk2(SharedLibraryInfo info) {
        if (info.getCertDigests().isEmpty() || info.getName() == null) {
            return false;
        }
        return info.getName().equals(LIB_NAME_SDK_2)
            && info.getCertDigests().get(0).equals(mCertDigest);
    }

    /**
     * Send a non-existing session-id to system.
     */
    private void testInvalidSessionId(DependencyInstallerCallback callback) throws Exception {

        // Pass a session id that doesn't exist
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            callback.onAllDependenciesResolved(new int[] {100});
                        });

        assertThat(exception).hasMessageThat().contains("Failed to find session: 100");

        // Fail the resolution to resume the original install flow.
        callback.onFailureToResolveAllDependencies();
    }

    /**
     * Send a session id that has already been abandoned.
     */
    private void testAbandonedSessionId(DependencyInstallerCallback callback) throws Exception {

        SessionParams params = new SessionParams(MODE_FULL_INSTALL);
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        int sessionId = installer.createSession(params);
        // Register a listener for this session id
        SessionListener sessionListener = new SessionListener(sessionId);
        try {
            getContext().getPackageManager().getPackageInstaller().registerSessionCallback(
                    sessionListener,
                    new Handler(Looper.getMainLooper()));

            // Abandon the session
            Session session = installer.openSession(sessionId);
            session.abandon();

            // Wait for session to finish
            sessionListener.latch.await(5, TimeUnit.SECONDS);
        } finally {
            getContext().getPackageManager().getPackageInstaller().unregisterSessionCallback(
                    sessionListener);
        }

        // Pass an abandoned session id
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            callback.onAllDependenciesResolved(new int[] {sessionId});
                        });

        assertThat(exception).hasMessageThat().contains("Session already finished: " + sessionId);

        // Fail the resolution to resume the original install flow.
        callback.onFailureToResolveAllDependencies();
    }

    private void testAbandonSessionDuringInstall(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback) throws Exception {

        assertThat(neededLibraries).hasSize(1);
        SharedLibraryInfo info = neededLibraries.get(0);

        // Create two sessions and have system wait for them
        List<Integer> sessionIds = createSessionIds(2);
        callback.onAllDependenciesResolved(toIntArray(sessionIds));

        // Now we commit the first session and then abandon the second. The system should
        // be able to detect session being abandoned and stop waiting.

        int firstSession = sessionIds.get(0);
        SyncBroadcastReceiver sender = new SyncBroadcastReceiver(List.of(firstSession));
        Session session = writeToSession(info, firstSession);
        session.commit(sender.getIntentSender(this));
        assertThat(sender.latch.await(WAIT_FOR_INSTALL_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(sender.isFailure).isFalse();

        // Now hat first session has finished, abandon the second session
        int secondSession = sessionIds.get(1);
        session = writeToSession(info, secondSession);
        session.abandon();
    }

    private void testResumeOnFailureFailsInstall(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback) throws Exception {

        // Create a session and have system wait for them
        List<Integer> sessionIds = createSessionIds(1);
        callback.onAllDependenciesResolved(toIntArray(sessionIds));

        // Now we commit the session without writing to it. This ensures it will fail installation.
        int firstSession = sessionIds.get(0);
        SyncBroadcastReceiver sender = new SyncBroadcastReceiver(List.of(firstSession));
        Session session = writeToSession(null, firstSession);
        session.commit(sender.getIntentSender(this));
    }

    private void installDependenciesSync(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback) throws Exception {

        int size = neededLibraries.size();
        List<Integer> sessionIds = createSessionIds(neededLibraries.size());

        SyncBroadcastReceiver sender = new SyncBroadcastReceiver(sessionIds);
        for (int i = 0; i < size; i++) {
            int sessionId = sessionIds.get(i);
            SharedLibraryInfo info = neededLibraries.get(i);
            Session session = writeToSession(info, sessionId);
            session.commit(sender.getIntentSender(this));
        }

        // Wait for all sessions to finish installation
        assertThat(sender.latch.await(WAIT_FOR_INSTALL_MS, TimeUnit.MILLISECONDS)).isTrue();
        if (sender.isFailure) {
            callback.onFailureToResolveAllDependencies();
        } else {
            callback.onAllDependenciesResolved(toIntArray(sessionIds));
        }
    }

    private void installDependenciesAsync(List<SharedLibraryInfo> neededLibraries,
            DependencyInstallerCallback callback) throws Exception {

        int size = neededLibraries.size();
        List<Integer> sessionIds = createSessionIds(neededLibraries.size());

        // Return the session ids immediately
        callback.onAllDependenciesResolved(toIntArray(sessionIds));

        SyncBroadcastReceiver sender = new SyncBroadcastReceiver(sessionIds);
        for (int i = 0; i < size; i++) {
            int sessionId = sessionIds.get(i);
            SharedLibraryInfo info = neededLibraries.get(i);
            Session session = writeToSession(info, sessionId);
            session.commit(sender.getIntentSender(this));
        }
    }

    private List<Integer> createSessionIds(int size) throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        List<Integer> sessionIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            SessionParams params = new SessionParams(MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            Log.i(TAG, "Session created: " + sessionId);
            sessionIds.add(sessionId);
        }
        return sessionIds;
    }

    private Session writeToSession(@Nullable SharedLibraryInfo info, int sessionId)
                throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        Session session = installer.openSession(sessionId);
        if (info == null) {
            return session;
        }
        if (info.getName().equals(LIB_NAME_SDK_1)) {
            writeApk(session, TEST_SDK1_APK_NAME);
        } else if (info.getName().equals(LIB_NAME_SDK_2)) {
            writeApk(session, TEST_SDK2_APK_NAME);
        }
        return session;
    }

    private void validateNeededLibraries(List<SharedLibraryInfo> neededLibraries) throws Exception {
        for (SharedLibraryInfo info: neededLibraries) {
            if (isSdk1(info)) {
                Log.i(TAG, "SDK1 missing dependency found");
                continue;
            }

            if (isSdk2(info)) {
                Log.i(TAG, "SDK2 missing dependency found");
                continue;
            }
            // For everything else, fail.
            throw new IllegalStateException("Unsupported SDK found: " + info.getName() + " "
                    + info.getCertDigests().get(0));
        }
    }

    private String getMethodName() {
        return getDefaultSharedPreferences().getString(METHOD_NAME, "");
    }

    private void setErrorMessage(String msg) {
        getDefaultSharedPreferences().edit().putString(ERROR_MESSAGE, msg).commit();
    }

    private void writeApk(@NonNull Session session, @NonNull String name) throws IOException {
        String apkPath = createApkPath(name);
        try (InputStream in = new FileInputStream(apkPath)) {
            try (OutputStream out = session.openWrite(name, 0, -1)) {
                copyStream(in, out);
            }
        }
    }

    private String getPackageCertDigest(String packageName) throws Exception {
        PackageInfo sdkPackageInfo = getPackageManager().getPackageInfo(packageName,
                PackageManager.PackageInfoFlags.of(
                    GET_SIGNING_CERTIFICATES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
        SigningInfo signingInfo = sdkPackageInfo.signingInfo;
        Signature[] signatures =
            signingInfo != null ? signingInfo.getSigningCertificateHistory() : null;
        byte[] digest = PackageUtils.computeSha256DigestBytes(signatures[0].toByteArray());
        return new String(HexEncoding.encode(digest));
    }

    private boolean isInstrumented() throws Exception {
        try {
            InstrumentationRegistry.getContext();
            return true;
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("No instrumentation registered!")) {
                return false;
            }
            throw e;
        }
    }

    private static class SyncBroadcastReceiver extends BroadcastReceiver {
        private final int[] mSessionIds;
        private int mPendingSessionCount;

        public final CountDownLatch latch = new CountDownLatch(1);
        public boolean isFailure = false;

        SyncBroadcastReceiver(List<Integer> sessionIds) {
            mSessionIds = toIntArray(sessionIds);
            mPendingSessionCount = sessionIds.size();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received intent " + prettyPrint(intent));
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status != PackageInstaller.STATUS_SUCCESS) {
                isFailure = true;
            }
            synchronized (this) {
                mPendingSessionCount--;
                if (mPendingSessionCount == 0) {
                    latch.countDown();
                }
            }
        }

        public IntentSender getIntentSender(Context context) {
            String action = TestDependencyInstallerService.class.getName()
                    + SystemClock.elapsedRealtime();
            context.registerReceiver(this, new IntentFilter(action),
                    Context.RECEIVER_EXPORTED);
            Intent intent = new Intent(action).setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, FLAG_MUTABLE);
            return pending.getIntentSender();
        }

        private static String prettyPrint(Intent intent) {
            int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            return String.format("%s: {\n"
                    + "sessionId = %d\n"
                    + "status = %d\n"
                    + "message = %s\n"
                    + "}", intent, sessionId, status, message);
        }
    }

    static class SessionListener extends PackageInstaller.SessionCallback {

        public final CountDownLatch latch = new CountDownLatch(1);

        private final int mSessionId;

        SessionListener(int sessionId) {
            mSessionId = sessionId;
        }

        @Override
        public void onCreated(int sessionId) {
        }

        @Override
        public void onBadgingChanged(int sessionId) {
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) {
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            if (sessionId == mSessionId) {
                latch.countDown();
            }
        }
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName + ".apk";
    }

    private SharedPreferences getDefaultSharedPreferences() {
        final Context appContext = getContext().getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private  Context getContext() {
        return this;
    }

    private static int[] toIntArray(List<Integer> list) {
        return list.stream().mapToInt(i->i).toArray();
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        int total = 0;
        byte[] buffer = new byte[8192];
        int c;
        while ((c = in.read(buffer)) != -1) {
            total += c;
            out.write(buffer, 0, c);
        }
    }

}
