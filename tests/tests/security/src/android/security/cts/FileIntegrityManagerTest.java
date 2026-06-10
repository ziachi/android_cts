/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.platform.test.annotations.AppModeFull;
import android.security.FileIntegrityManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class FileIntegrityManagerTest {

    private static final String TAG = "FileIntegrityManagerTest";
    private static final String FILENAME = "test.file";
    private static final String TEST_FILE_CONTENT = "fs-verity";
    // Produced by shell command: echo -n 'fs-verity' > t; fsverity digest --compact t
    private static final String TEST_FILE_DIGEST =
            "1e5300d45dce778c0aa6ced72954a8a4398d8f5d590c73cb431fe5fe2adbeeed";
    private static final int MIN_REQUIRED_API_LEVEL = 30;

    private Context mContext;
    private FileIntegrityManager mFileIntegrityManager;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            android.Manifest.permission.SETUP_FSVERITY);

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // This feature name check only applies to devices that first shipped with
        // SC or later.
        final int firstApiLevel =
                Math.min(PropertyUtil.getFirstApiLevel(), PropertyUtil.getVendorApiLevel());
        if (firstApiLevel >= Build.VERSION_CODES.S) {
            // Assumes every test in this file asserts a requirement of CDD section 9.
            assumeTrue("Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.",
                    mContext.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_SECURITY_MODEL_COMPATIBLE));
        }

        mFileIntegrityManager = mContext.getSystemService(FileIntegrityManager.class);
    }

    @After
    public void tearDown() throws Exception {
        var files = newSupportedFiles();
        files.addAll(newUnsupportedFiles());
        for (var file : files) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @CddTest(requirement="9.10/C-0-3,C-1-1")
    @Test
    public void testSupportedOnDevicesFirstLaunchedWithR() throws Exception {
        if (PropertyUtil.getFirstApiLevel() >= MIN_REQUIRED_API_LEVEL) {
            assertTrue(mFileIntegrityManager.isApkVeritySupported());
        }
    }

    @Test
    @ApiTest(apis = {"android.security.FileIntegrityManager#setupFsVerity",
            "android.security.FileIntegrityManager#getFsVerityDigest"})
    public void testEnableAndMeasureFsVerityByFile() throws Exception {
        var files = newSupportedFiles();
        for (var file : files) {
            Log.d(TAG, "testEnableAndMeasureFsVerityByFile: " + file);

            createTestFile(file);
            mFileIntegrityManager.setupFsVerity(file);

            byte[] actualDigest = mFileIntegrityManager.getFsVerityDigest(file);
            assertThat(actualDigest).isNotNull();
            assertThat(HexFormat.of().formatHex(actualDigest)).isEqualTo(TEST_FILE_DIGEST);
        }
    }

    @Test
    @ApiTest(apis = {"android.security.FileIntegrityManager#setupFsVerity"})
    public void testFailToEnableUnsupportedLocation() throws Exception {
        var files = newUnsupportedFiles();
        for (var file : files) {
            Log.d(TAG, "testFailToEnableUnsupportedLocation: " + file);

            createTestFile(file);
            assertThrows(Exception.class, () -> mFileIntegrityManager.setupFsVerity(file));
        }
    }

    @Test
    @ApiTest(apis = {"android.security.FileIntegrityManager#setupFsVerity"})
    public void testFailToEnableWithOpenedWritableFd() throws Exception {
        var files = newSupportedFiles();
        for (var file : files) {
            Log.d(TAG, "testFailToEnableWithOpenedWritableFd: " + file);

            var fos = new FileOutputStream(file);
            fos.write(TEST_FILE_CONTENT.getBytes());

            // With any writable fd, the call will fail.
            assertThrows(IOException.class, () ->
                    mFileIntegrityManager.setupFsVerity(file));
        }
    }

    @Test
    @ApiTest(apis = {"android.security.FileIntegrityManager#getFsVerityDigest"})
    public void testMeasureWithoutFsVerity() throws Exception {
        var files = newSupportedFiles();
        for (var file : files) {
            Log.d(TAG, "testMeasureWithoutFsVerity: " + file);

            createTestFile(file);

            byte[] actualDigest = mFileIntegrityManager.getFsVerityDigest(file);
            assertThat(actualDigest).isNull();
        }
    }

    private void createTestFile(File file) throws IOException {
        try (var fos = new FileOutputStream(file)) {
            fos.write(TEST_FILE_CONTENT.getBytes());
        }
    }

    private List<File> newSupportedFiles() {
        var ceContext = mContext.createCredentialProtectedStorageContext();
        var deContext = mContext.createDeviceProtectedStorageContext();
        return new ArrayList<>(Arrays.asList(
                new File(ceContext.getDataDir(), FILENAME),
                new File(ceContext.getFilesDir(), FILENAME),
                new File(deContext.getDataDir(), FILENAME),
                new File(deContext.getFilesDir(), FILENAME)));
    }

    private List<File> newUnsupportedFiles() {
        return new ArrayList<>(Arrays.asList(
                new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILENAME),
                new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), FILENAME)));
    }
}
