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

package android.scopedstorage.cts.device;

import static android.scopedstorage.cts.device.OtherAppFilesRule.GrantModifications.GRANT;
import static android.scopedstorage.cts.device.OtherAppFilesRule.modifyReadAccess;
import static android.scopedstorage.cts.device.OwnedFilesRule.RESOURCE_ID_WITH_METADATA;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertCannotReadOrWrite;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertFileAccess_listFiles;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertFileAccess_readOnly;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMismatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromFile;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromRawResource;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_canReadThumbnail;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_listFiles;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_noReadNoWrite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_noWrite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_readOnly;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_readWrite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriDoesNotExist;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriIsFavorite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriIsNotFavorite;
import static android.scopedstorage.cts.lib.TestUtils.canOpenFileAs;
import static android.scopedstorage.cts.lib.TestUtils.doEscalation;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;
import static android.scopedstorage.cts.lib.TestUtils.readExifMetadataFromTestApp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.TestApp;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public class StorageOtherFilesTest {

    protected static final String TAG = "StorageOtherFilesTest";
    private static final String THIS_PACKAGE_NAME =
            ApplicationProvider.getApplicationContext().getPackageName();

    private static final TestApp APP_VU_SELECTED = new TestApp("TestAppVUSelected",
            "android.scopedstorage.cts.testapp.VUSelected", 1, false,
            "CtsScopedStorageTestAppVUSelected.apk");
    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private static final Context sContext = sInstrumentation.getContext();
    private static final ContentResolver sContentResolver = getContentResolver();

    @ClassRule
    public static final OtherAppFilesRule sFilesRule = new OtherAppFilesRule(sContentResolver);

    private static final File IMAGE_FILE_READABLE = OtherAppFilesRule.getImageFile1();
    private static final File IMAGE_FILE_NO_ACCESS = OtherAppFilesRule.getImageFile2();

    private static final File VIDEO_FILE_READABLE = OtherAppFilesRule.getVideoFile1();
    private static final File VIDEO_FILE_NO_ACCESS = OtherAppFilesRule.getVideoFile2();

    // Cannot be static as the underlying resource isn't
    private final Uri mImageUriReadable = sFilesRule.getImageUri1();
    private final Uri mImageUriNoAccess = sFilesRule.getImageUri2();
    private final Uri mVideoUriReadable = sFilesRule.getVideoUri1();
    private final Uri mVideoUriNoAccess = sFilesRule.getVideoUri2();

    @Rule public final TestRule mCompatChangeRule = new PlatformCompatChangeRule();
    public static final long LIMIT_CREATE_REQUEST_URIS = 203408344L;

    @BeforeClass
    public static void init() throws Exception {
        pollForPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, true);
        // creating grants only for one
        modifyReadAccess(IMAGE_FILE_READABLE, THIS_PACKAGE_NAME, GRANT);
        modifyReadAccess(VIDEO_FILE_READABLE, THIS_PACKAGE_NAME, GRANT);
    }

    @Before
    public void setUp() {
        DeviceTestUtils.checkUISupported();
    }

    @Test
    public void other_listMediaFiles() throws Exception {
        Set<File> expectedValues = Set.of(IMAGE_FILE_READABLE, VIDEO_FILE_READABLE);
        Set<File> notExpected = Set.of(IMAGE_FILE_NO_ACCESS, VIDEO_FILE_NO_ACCESS);
        // File access
        assertFileAccess_listFiles(
                IMAGE_FILE_READABLE.getParentFile(), expectedValues, notExpected);
        // Query DCIM
        assertResolver_listFiles(
                Environment.DIRECTORY_DCIM, expectedValues, notExpected, sContentResolver);
    }

    @Test
    public void other_readVisualMediaFiles() throws Exception {
        assertResolver_readOnly(mImageUriReadable, sContentResolver);
        assertResolver_readOnly(mVideoUriReadable, sContentResolver);
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);
        assertResolver_noReadNoWrite(mVideoUriNoAccess, sContentResolver);

        assertFileAccess_readOnly(IMAGE_FILE_READABLE);
        assertFileAccess_readOnly(VIDEO_FILE_READABLE);
        assertCannotReadOrWrite(IMAGE_FILE_NO_ACCESS);
        assertCannotReadOrWrite(VIDEO_FILE_NO_ACCESS);
    }

    @Test
    public void other_readThumbnails() throws Exception {
        assertResolver_canReadThumbnail(mImageUriReadable, sContentResolver);
        // TODO b/216249186 check file permissions for thumbnails
        // It is currently not working MediaProvider#8285
        // assertResolver_cannotReadThumbnail(mImageUriNoAccess, sContentResolver);
        // TODO Video thumbnails
    }

    @Test
    public void other_createWriteRequest() throws Exception {
        doEscalation(
                MediaStore.createWriteRequest(
                        sContentResolver, Collections.singletonList(mImageUriReadable)));
        assertResolver_readWrite(mImageUriReadable, sContentResolver);
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);

        sInstrumentation
                .getContext()
                .revokeUriPermission(mImageUriReadable, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertResolver_noWrite(mImageUriReadable, sContentResolver);
    }

    @Test
    public void other_createFavoriteRequest() throws Exception {
        doEscalation(
                MediaStore.createFavoriteRequest(
                        sContentResolver,
                        Arrays.asList(mImageUriReadable, mImageUriNoAccess),
                        true));
        assertResolver_uriIsFavorite(mImageUriReadable, sContentResolver);
        // We still don't have access to uri 2 to be able to check if it is favorite
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);

        doEscalation(
                MediaStore.createFavoriteRequest(
                        sContentResolver,
                        Arrays.asList(mImageUriReadable, mImageUriNoAccess),
                        false));
        assertResolver_uriIsNotFavorite(mImageUriReadable, sContentResolver);
        assertResolver_noReadNoWrite(mImageUriNoAccess, sContentResolver);
    }

    @Test
    public void other_deleteRequest() throws Exception {
        File fileToBeDeleted1 = new File(getDcimDir(), TAG + "_delete_1.jpg");
        File fileToBeDeleted2 = new File(getDcimDir(), TAG + "_delete_2.jpg");
        try {
            Uri uriToBeDeleted1 = sFilesRule.createEmptyFileAsOther(fileToBeDeleted1);
            Uri uriToBeDeleted2 = sFilesRule.createEmptyFileAsOther(fileToBeDeleted2);
            modifyReadAccess(fileToBeDeleted1, THIS_PACKAGE_NAME, GRANT);

            doEscalation(
                    MediaStore.createDeleteRequest(
                            sContentResolver, Arrays.asList(uriToBeDeleted1, uriToBeDeleted2)));
            assertResolver_uriDoesNotExist(uriToBeDeleted1, sContentResolver);
            assertResolver_uriDoesNotExist(uriToBeDeleted2, sContentResolver);
        } finally {
            fileToBeDeleted1.delete();
            fileToBeDeleted2.delete();
        }
    }

    @Test
    public void testDeleteWithParamDeleteData() throws Exception {
        int expectedTargetSdk = sContext.getPackageManager().getApplicationInfo(
                THIS_PACKAGE_NAME, PackageManager.ApplicationInfoFlags.of(0)).targetSdkVersion;
        assumeTrue(expectedTargetSdk > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        File testFile = stageImageFile("test" + System.nanoTime() + ".jpg",
                RESOURCE_ID_WITH_METADATA);
        final Uri uri = MediaStore.scanFile(sContentResolver, testFile);
        String path;
        try (Cursor c = sContentResolver.query(uri, new String[]{MediaColumns.DATA}, null, null,
                null)) {
            c.moveToNext();
            path = c.getString(c.getColumnIndex(MediaColumns.DATA));
        }

        assertTrue(new File(path).exists());

        // Delete with param "deletedata" as false
        sContentResolver.delete(
                uri.buildUpon().appendQueryParameter("deletedata", "false").build(), /* extras */
                null);

        // File should be deleted despite "deletedata" as false
        assertFalse(new File(path).exists());
    }

    @Test
    public void other_accessLocationMetadata() throws Exception {
        // The current application has access to ACCESS_MEDIA_LOCATION
        HashMap<String, String> originalExif =
                getExifMetadataFromRawResource(RESOURCE_ID_WITH_METADATA);
        pollForPermission(Manifest.permission.ACCESS_MEDIA_LOCATION, true);
        assertExifMetadataMatch(getExifMetadataFromFile(IMAGE_FILE_READABLE), originalExif);

        // This application doesn't, but it is given a grant
        pollForPermission(APP_VU_SELECTED, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                true);
        modifyReadAccess(IMAGE_FILE_READABLE, APP_VU_SELECTED.getPackageName(), GRANT);
        assertThat(canOpenFileAs(APP_VU_SELECTED, IMAGE_FILE_READABLE, /*forWrite*/ false))
                .isTrue();
        HashMap<String, String> exifFromTestApp =
                readExifMetadataFromTestApp(APP_VU_SELECTED, IMAGE_FILE_READABLE.getPath());
        assertExifMetadataMismatch(exifFromTestApp, originalExif);
    }

    private Collection<Uri> createTestFiles(int numFiles) {
        Collection<Uri> uris = new ArrayList<Uri>();

        for (int i = 0; i < numFiles; i++) {
            final ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "testFile_" + i + ".png");
            Uri fileUri =
                    sContentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            uris.add(fileUri);
        }

        assertThat(uris.size()).isEqualTo(numFiles);
        return uris;
    }

    private void deleteTestFiles(Collection<Uri> uris) {
        // Delete all files created during the test
        for (Uri uri : uris) {
            sContentResolver.delete(uri, null, null);
        }

        // Assert that files are deleted from the Mediaprovider database
        try (Cursor c =
                sContentResolver.query(
                        uris.iterator().next(),
                        new String[] {MediaColumns.DATA},
                        null,
                        null,
                        null)) {
            assertThat(c.getCount()).isEqualTo(0);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    @EnableCompatChanges({LIMIT_CREATE_REQUEST_URIS})
    public void testCreateRequestLimitUris_throwsIllegalArgumentException() throws Exception {
        assumeTrue(sContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.BAKLAVA);

        final int numFiles = 2100;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            // Assert that files are inserted in the Mediaprovider database
            try (Cursor c =
                    sContentResolver.query(
                            uris.iterator().next(),
                            new String[] {MediaColumns.DATA},
                            null,
                            null,
                            null)) {
                assertThat(c.getCount()).isEqualTo(1);
            }

            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createWriteRequest(sContentResolver, uris));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createDeleteRequest(sContentResolver, uris));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createFavoriteRequest(sContentResolver, uris, true));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> MediaStore.createTrashRequest(sContentResolver, uris, true));
        } finally {
            deleteTestFiles(uris);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    @EnableCompatChanges({LIMIT_CREATE_REQUEST_URIS})
    public void testCreateRequestLimitUris_success() throws Exception {
        final int numFiles = 2000;
        Collection<Uri> uris = createTestFiles(numFiles);

        try {
            // Assert that files are inserted in the Mediaprovider database
            try (Cursor c =
                    sContentResolver.query(
                            uris.iterator().next(),
                            new String[] {MediaColumns.DATA},
                            null,
                            null,
                            null)) {
                assertThat(c.getCount()).isEqualTo(1);
            }

            PendingIntent pi = MediaStore.createWriteRequest(sContentResolver, uris);
            assertNotNull(pi);
            doEscalation(pi, true, true, true);

            pi = MediaStore.createFavoriteRequest(sContentResolver, uris, true);
            assertNotNull(pi);
            doEscalation(pi);

            pi = MediaStore.createTrashRequest(sContentResolver, uris, true);
            assertNotNull(pi);
            doEscalation(pi, true, true, true);

            pi = MediaStore.createDeleteRequest(sContentResolver, uris);
            assertNotNull(pi);
            doEscalation(pi, true, true, true);

        } finally {
            // Assert that files are deleted from the Mediaprovider database
            try (Cursor c =
                    sContentResolver.query(
                            uris.iterator().next(),
                            new String[] {MediaColumns.DATA},
                            null,
                            null,
                            null)) {
                assertThat(c.getCount()).isEqualTo(0);
            }
        }
    }

    private File stageImageFile(String name, int sourceId) throws Exception {
        final File img = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), name);

        try (InputStream in = sContext.getResources().openRawResource(sourceId);
             OutputStream out = new FileOutputStream(img)) {
            // Dump the image we have to external storage
            FileUtils.copy(in, out);
        }

        return img;
    }
}
