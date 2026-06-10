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

package android.security.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* End to end tests for CertBlocklist
 *
 * The blocklist is tested by:
 *  - Updating Settings.Secure.pubkey_blacklist with a new value.
 *  - Checking that /data/misc/keychain/pubkey_blacklist.txt is updated
 *    correctly.
 *  - Spinning up an app that uses the default TrustManager and checking that
 *    the certificate is correctly blocked, even when added to the KeyStore.
 */
@RunWith(AndroidJUnit4.class)
public class CertBlocklistFileTest {
    private static final String TAG = "CertBlocklistFileTest";

    private static final String PUBKEY_BLOCKLIST_SETTING = "pubkey_blacklist";
    private static final String PUBKEY_PATH = "/data/misc/keychain/pubkey_blacklist.txt";

    // SHA1 of the public key for blocklist_file_test_ca.pem.
    private static final String SHA1_BLOCKLIST_TEST_CA_PUBKEY =
            "cb82aea92fca9b8b5c3b2796403e897e8efe99cb";

    private static final String TEST_APP_PKG = "android.security.cts.certblocklisttestapp";
    private static final String TEST_APP_LOCATION =
            "/data/local/tmp/cts/security/CertBlocklistTestApp.apk";
    private static final int SERVICE_TIMEOUT_SEC = 5;

    private Context mContext;
    private volatile ServiceConnection mServiceConnection;
    private String mPreviousPubKey;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        UserManager userManager = mContext.getSystemService(UserManager.class);
        assumeTrue("Skip test: not system user", userManager.isSystemUser());

        ShellUtils.runShellCommand("pm install " + TEST_APP_LOCATION);
        mPreviousPubKey =
          Settings.Secure.getString(mContext.getContentResolver(),
              PUBKEY_BLOCKLIST_SETTING);
        if (mPreviousPubKey == null) {
            mPreviousPubKey = "";
        }
    }

    @After
    public void tearDown() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.Secure.putString(mContext.getContentResolver(),
                      PUBKEY_BLOCKLIST_SETTING, mPreviousPubKey);
        });
        if (mServiceConnection != null) {
            mContext.unbindService(mServiceConnection);
        }
        ShellUtils.runShellCommand("pm uninstall " + TEST_APP_PKG);
    }

    private static final class ServiceConnection implements android.content.ServiceConnection {
        private volatile CompletableFuture<ICertCheckService> mFuture = new CompletableFuture<>();

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mFuture.complete(ICertCheckService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mFuture = new CompletableFuture<>();
        }

        public ICertCheckService get() {
            try {
                return mFuture.get(SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                throw new RuntimeException("Unable to reach CertTestService: " + e.toString());
            }
        }
    }

    private void updateBlocklist(String content) throws Exception {
        Log.i(TAG, "Updating secure settings pubkey_blacklist");
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.Secure.putString(mContext.getContentResolver(),
                    PUBKEY_BLOCKLIST_SETTING, content);
        });

        Log.i(TAG, "Waiting for updated file");
        TestUtils.waitUntil(PUBKEY_PATH + " should be readable",
                () -> new File(PUBKEY_PATH).canRead());
        Log.i(TAG, "File is readable");

        TestUtils.waitUntil(PUBKEY_PATH + " does not contain test public key", () -> {
            String text = new String(Files.readAllBytes(Paths.get(PUBKEY_PATH)),
                    StandardCharsets.UTF_8);
            return text.contains(content);
        });
        Log.i(TAG, "File contains public key");
    }

    private void connectToTestApp() {
        mServiceConnection = new ServiceConnection();
        Intent intent = new Intent();
        intent.setClassName(TEST_APP_PKG, TEST_APP_PKG + ".CertTestService");
        assertTrue(mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE));
        Log.i(TAG, "Connected to CertTestService");
    }

    @Test
    public void testPubKeySha1() throws Exception {
        updateBlocklist(SHA1_BLOCKLIST_TEST_CA_PUBKEY);

        connectToTestApp();

        InputStream is = mContext.getResources().openRawResource(R.raw.blocklist_file_test_ca);
        String certificate = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(mServiceConnection.get().isBlocked(certificate));
    }

    @Test
    public void testPubKeyEmpty() throws Exception {
        updateBlocklist("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        connectToTestApp();

        InputStream is = mContext.getResources().openRawResource(R.raw.blocklist_file_test_ca);
        String certificate = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        assertFalse(mServiceConnection.get().isBlocked(certificate));
    }
}
