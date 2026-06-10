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

package android.security.net.config.cts;

import static android.security.net.config.cts.CertificateTransparencyTestUtils.CT_DIRECTORY_NAME;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.CT_PARENT_DIRECTORY_PATH;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.HTTP_OK_RESPONSE_CODE;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.NO_SCT_PROVIDED_DOMAIN;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.deleteLogList;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.downloadLogList;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.isLogListFilePresent;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.os.FileObserver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    Flags.FLAG_CERTIFICATE_TRANSPARENCY_CONFIGURATION,
    com.android.org.conscrypt.flags.Flags.FLAG_CERTIFICATE_TRANSPARENCY_PLATFORM
})
public class SctValidationLogListDownloadTest extends BaseTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    public static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private static CountDownLatch sCountDownLatch;
    private static CTDirectoryFileObserver sFileObserver = new CTDirectoryFileObserver();

    private static final String TAG = "SctValidationLogListDownloadTest";

    private static class CTDirectoryFileObserver extends FileObserver {
        CTDirectoryFileObserver() {
            super(new File(CT_PARENT_DIRECTORY_PATH), FileObserver.CREATE);
        }

        @Override
        public void onEvent(int event, String path) {
            if (CT_DIRECTORY_NAME.equals(path)) {
                sCountDownLatch.countDown();
            }
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        sFileObserver.startWatching();
        downloadLogList();

        // Wait until the CT directory is created
        sCountDownLatch = new CountDownLatch(1);

        if (!sCountDownLatch.await(30L, TimeUnit.SECONDS)) {
            // Continue onwards as the tests will be skipped
            Log.d(TAG, "Took too long to download log list, skipping test");
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        deleteLogList();
        sFileObserver.stopWatching();
    }

    @Test
    public void testCTVerification_whenLogListDownloaded_sctDomain_connectionSucceeds()
            throws IOException {
        assumeTrue(isLogListFilePresent());
        URL url = new URL(SCT_PROVIDED_DOMAIN);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.connect();

        assertEquals(urlConnection.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        urlConnection.disconnect();
    }

    @Test
    public void testCTVerification_whenLogListDownloaded_noSctDomain_exceptionsThrown()
            throws IOException {
        assumeTrue(isLogListFilePresent());
        URL url = new URL(NO_SCT_PROVIDED_DOMAIN);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        SSLHandshakeException expected =
                assertThrows(SSLHandshakeException.class, () -> urlConnection.connect());

        assertThat(expected.getCause()).isInstanceOf(CertificateException.class);
        assertTrue(expected.getMessage().contains("NOT_ENOUGH_SCTS"));
    }
}
