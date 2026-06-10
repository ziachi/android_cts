/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.cts.blob;

import static com.android.cts.blob.BlobStoreManagerTest.HELPER_PKG2;
import static com.android.cts.blob.BlobStoreManagerTest.HELPER_PKG2_CERT_SHA256;
import static com.android.cts.blob.BlobStoreManagerTest.HELPER_PKG3;
import static com.android.cts.blob.BlobStoreManagerTest.HELPER_PKG3_CERT_SHA256;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.blob.BlobStoreManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;
import com.android.utils.blob.FakeBlobData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(BlobStoreTestRunner.class)
public class BlobStoreManagerBoundsTest extends StsExtraBusinessLogicTestCase {
    // TODO: b/404309424 - Avoid copy-pasting these constants from BlobStoreManager
    // Source BlobStoreManager#MAX_PACKAGE_NAME_LENGTH
    private static final int MAX_PACKAGE_NAME_LENGTH = 223;
    // Source BlobStoreManager#MAX_CERTIFICATE_LENGTH
    private static final int MAX_CERTIFICATE_LENGTH = 32;

    private Context mContext;
    private BlobStoreManager mBlobStoreManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mBlobStoreManager =
                (BlobStoreManager) mContext.getSystemService(Context.BLOB_STORE_SERVICE);
    }

    @Test
    public void testPakageNameExceedsLimit() throws Exception {
        final FakeBlobData blobData = new FakeBlobData.Builder(mContext).build();
        blobData.prepare();
        try {
            final long sessionId = mBlobStoreManager.createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowPackageAccess(HELPER_PKG2, HELPER_PKG2_CERT_SHA256);
                final String longPkgName = "a".repeat(MAX_PACKAGE_NAME_LENGTH + 1);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> session.allowPackageAccess(longPkgName, HELPER_PKG3_CERT_SHA256));
            } finally {
                mBlobStoreManager.abandonSession(sessionId);
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testCertificateExceedsLimit() throws Exception {
        final FakeBlobData blobData = new FakeBlobData.Builder(mContext).build();
        blobData.prepare();
        try {
            final long sessionId = mBlobStoreManager.createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowPackageAccess(HELPER_PKG2, HELPER_PKG2_CERT_SHA256);
                final byte[] longCertificate = new byte[MAX_CERTIFICATE_LENGTH + 1];
                Arrays.fill(longCertificate, (byte) 'a');
                assertThrows(
                        IllegalArgumentException.class,
                        () -> session.allowPackageAccess(HELPER_PKG3, longCertificate));
            } finally {
                mBlobStoreManager.abandonSession(sessionId);
            }
        } finally {
            blobData.delete();
        }
    }
}
