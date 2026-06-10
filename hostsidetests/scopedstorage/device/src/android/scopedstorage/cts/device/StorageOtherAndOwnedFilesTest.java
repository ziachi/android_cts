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

package android.scopedstorage.cts.device;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
import static android.scopedstorage.cts.device.OtherAppFilesRule.GrantModifications.GRANT;
import static android.scopedstorage.cts.device.OtherAppFilesRule.GrantModifications.REVOKE;
import static android.scopedstorage.cts.device.OtherAppFilesRule.modifyReadAccess;
import static android.scopedstorage.cts.device.OwnedAndOtherFilesRule.getResultForFilesQuery;
import static android.scopedstorage.cts.lib.TestUtils.executeShellCommand;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalMediaDir;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.MediaStore;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.util.List;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public class StorageOtherAndOwnedFilesTest {

    protected static final String TAG = "StorageOtherAndOwnedFilesTest";

    private static final ContentResolver sContentResolver = getContentResolver();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final TestRule mCompatChangeRule = new PlatformCompatChangeRule();
    public static final long ENABLE_OWNED_PHOTOS = 310703690L;

    @ClassRule
    public static final OwnedAndOtherFilesRule sFilesRule =
            new OwnedAndOtherFilesRule(sContentResolver);

    private static final String THIS_PACKAGE_NAME = ApplicationProvider.getApplicationContext()
            .getPackageName();

    private static final int TOTAL_OWNED_ITEMS = OwnedFilesRule.getAllFiles().size();

    /**
     * Inits test with correct permissions.
     */
    @BeforeClass
    public static void init() throws Exception {
        pollForPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, true);
    }

    @Before
    public void setUp() {
        DeviceTestUtils.checkUISupported();
    }

    @After
    public void cleanUp() throws IOException {
        // Clear all grants.
        for (File file : OtherAppFilesRule.getAllFiles()) {
            modifyReadAccess(file, THIS_PACKAGE_NAME, REVOKE);
        }
    }

    @RequiresFlagsEnabled("com.android.providers.media.flags.picker_recent_selection")
    @Test
    public void test_latestSelectionOnly_noGrantsPresent() {
        // Enable recent selection only in the queryArgs.
        Bundle queryArgs = new Bundle();
        queryArgs.putBoolean(MediaStore.QUERY_ARG_LATEST_SELECTION_ONLY, true);

        try (Cursor c = getResultForFilesQuery(sContentResolver, queryArgs)) {
            assertThat(c).isNotNull();
            // Now only recently selected items should be returned, in this case since there are no
            // grants 0 items should be returned.
            assertWithMessage("Expected number of items(only recently selected) is 0.")
                    .that(c.getCount()).isEqualTo(0);
        }
    }

    @RequiresFlagsEnabled("com.android.providers.media.flags.picker_recent_selection")
    @Test
    public void test_latestSelectionOnly_withOwnedAndGrantedItems() throws Exception {
        // Only owned items should be returned since no other file item as been granted;
        try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
            assertThat(c).isNotNull();
            assertWithMessage(
                    String.format("Expected number of owned items to be %s:", TOTAL_OWNED_ITEMS))
                    .that(c.getCount()).isEqualTo(TOTAL_OWNED_ITEMS);
        }

        List<File> otherAppFiles = OtherAppFilesRule.getAllFiles();
        assertWithMessage("Need at least 2 non owned items").that(otherAppFiles.size()).isAtLeast(
                2);

        // give access for 1 file.
        modifyReadAccess(otherAppFiles.get(0), THIS_PACKAGE_NAME, GRANT);

        // Verify owned + granted items are returned.
        try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
            assertThat(c).isNotNull();
            assertWithMessage(String.format("Expected number of items(owned + 1 granted) to be %d.",
                    TOTAL_OWNED_ITEMS + 1))
                    .that(c.getCount()).isEqualTo(TOTAL_OWNED_ITEMS + 1);
        }

        // grant one more item.
        modifyReadAccess(otherAppFiles.get(1), THIS_PACKAGE_NAME, GRANT);
        // Verify owned + granted items are returned.
        try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
            assertThat(c).isNotNull();
            assertWithMessage(String.format("Expected number of items(owned + 2 granted) to be %d.",
                    TOTAL_OWNED_ITEMS + 2))
                    .that(c.getCount()).isEqualTo(TOTAL_OWNED_ITEMS + 2);
        }

        // Now enable recent selection only in the queryArgs.
        Bundle queryArgs = new Bundle();
        queryArgs.putBoolean(MediaStore.QUERY_ARG_LATEST_SELECTION_ONLY, true);

        try (Cursor c = getResultForFilesQuery(sContentResolver, queryArgs)) {
            assertThat(c).isNotNull();
            // Now only recently selected item should be returned.
            assertWithMessage("Expected number of items(only recently selected) is 1.")
                    .that(c.getCount()).isEqualTo(1);
            final Uri expectedMediaUri = MediaStore.scanFile(sContentResolver,
                    otherAppFiles.get(1));
            c.moveToFirst();
            assertWithMessage("Expected item Uri was: " + expectedMediaUri).that(
                    c.getInt(c.getColumnIndex(
                            MediaStore.Files.FileColumns._ID))).isEqualTo(
                    ContentUris.parseId(expectedMediaUri));
        }
    }

    @Test
    @RequiresFlagsEnabled("com.android.providers.media.flags.revoke_access_owned_photos")
    @EnableCompatChanges({ENABLE_OWNED_PHOTOS})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRevokeOwnershipWhenOwnedPhotosEnabled() throws Exception {
        File testFile = new File(getDcimDir(), "testFile" + System.nanoTime() + ".jpg");
        testFile.createNewFile();
        try {
            /* Normal scenario, all files created by the app is owned by the app. */
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(TOTAL_OWNED_ITEMS + 1, c.getCount());
            }

            /*
             * Revoke access from testFile, of one of the owned files.
             * This will set owner_package_name as null in files table for this file.
             */
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, REVOKE);
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(TOTAL_OWNED_ITEMS, c.getCount());
            }

            /*
             * Grant access to testFile, not a owned file as access was previously revoked.
             * This will add entry in media_grants and give access to this package for this file.
             */
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, GRANT);
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(TOTAL_OWNED_ITEMS + 1, c.getCount());
            }
        } finally {
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, REVOKE);
            executeShellCommand("rm " + testFile);
        }
    }

    @Test
    @RequiresFlagsEnabled("com.android.providers.media.flags.revoke_access_owned_photos")
    @DisableCompatChanges({ENABLE_OWNED_PHOTOS})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRevokeOwnershipWhenOwnedPhotosDisabled() throws Exception {
        File testFile = new File(getDcimDir(), "testFile" + System.nanoTime() + ".jpg");
        testFile.createNewFile();
        try {
            /* Normal scenario, all files created by the app is owned by the app. */
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(TOTAL_OWNED_ITEMS + 1, c.getCount());
            }

            /*
             * Try to revoke access from testFile, of one of the owned files.
             * This will not be able to revoke access since owned photos is disabled.
             * So total owned photos remain the same.
             */
            modifyReadAccess(testFile, THIS_PACKAGE_NAME, REVOKE);
            try (Cursor c = getResultForFilesQuery(sContentResolver, getQueryArgsForMediaFiles())) {
                assertThat(c).isNotNull();
                assertEquals(TOTAL_OWNED_ITEMS + 1, c.getCount());
            }
        } finally {
            executeShellCommand("rm " + testFile);
        }
    }


    @Test
    @RequiresFlagsEnabled("com.android.providers.media.flags.revoke_access_owned_photos")
    @EnableCompatChanges({ENABLE_OWNED_PHOTOS})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRenameOperationInSharedStorageForOwnedPhotos() throws Exception {
        performRenameOperationsForDirectory(getDcimDir());
    }

    @Test
    @RequiresFlagsEnabled("com.android.providers.media.flags.revoke_access_owned_photos")
    @EnableCompatChanges({ENABLE_OWNED_PHOTOS})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    public void testRenameOperationInMediaDirectoryForOwnedPhotos() throws Exception {
        performRenameOperationsForDirectory(getExternalMediaDir());
    }

    private static void performRenameOperationsForDirectory(File dir) throws IOException {
        File testFile = new File(dir, "testFile_" + System.nanoTime() + ".jpg");
        File renamedFile1 = new File(dir, "renamed1_" + System.nanoTime() + ".jpg");
        File renamedFile2 = new File(dir, "renamed2_" + System.nanoTime() + ".jpg");
        File renamedFile3 = new File(dir, "renamed3_" + System.nanoTime() + ".jpg");
        testFile.createNewFile();

        try {
            // only test file should originally exists and all other files should not exist
            assertTrue(testFile.exists());
            assertFalse(renamedFile1.exists());
            assertFalse(renamedFile2.exists());
            assertFalse(renamedFile3.exists());

            // the test file is owned by the package and hence has write access
            // so we should be able to rename it
            assertTrue(testFile.renameTo(renamedFile1));
            assertTrue(renamedFile1.exists());
            assertFalse(testFile.exists());

            // Revoke access of renamedFile1 and try to rename it.
            // The package does not have write access and hence should not be able to rename it
            modifyReadAccess(renamedFile1, THIS_PACKAGE_NAME, REVOKE);
            assertFalse(renamedFile1.renameTo(renamedFile2));
            assertTrue(renamedFile1.exists());
            assertFalse(renamedFile2.exists());

            // Grant access of renamedFile1 and try to rename it.
            // The package would have read access but not write access.
            // It should not be able to rename the file
            modifyReadAccess(renamedFile1, THIS_PACKAGE_NAME, GRANT);
            assertFalse(renamedFile1.renameTo(renamedFile3));
            assertTrue(renamedFile1.exists());
            assertFalse(renamedFile3.exists());
        } finally {
            modifyReadAccess(renamedFile1, THIS_PACKAGE_NAME, REVOKE);
            executeShellCommand("rm " + testFile);
            executeShellCommand("rm " + renamedFile1);
            executeShellCommand("rm " + renamedFile2);
            executeShellCommand("rm " + renamedFile3);
        }
    }

    /**
     * The files table keeps records of directories and subdirectories where media is stored. When
     * query argument is passed as null, these rows are also returned as a part of cursor. This
     * method can be used to filter out these unwanted results when we only need media files.
     *
     * @return A Bundle containing the query arguments for filtering media files .
     */
    private Bundle getQueryArgsForMediaFiles() {
        Bundle queryArgs = new Bundle();
        queryArgs.putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                MEDIA_TYPE
                        + " IN ("
                        + MEDIA_TYPE_IMAGE
                        + ", "
                        + MEDIA_TYPE_VIDEO
                        + ", "
                        + MEDIA_TYPE_AUDIO
                        + ")");
        return queryArgs;
    }
}
