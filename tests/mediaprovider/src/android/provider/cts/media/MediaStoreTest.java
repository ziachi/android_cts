/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.provider.cts.media;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;

import com.android.providers.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Set;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RunWith(Parameterized.class)
public class MediaStoreTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    static final String TAG = "MediaStoreTest";

    private static final long SIZE_DELTA = 32_000;
    private static final String[] SYSTEM_GALERY_APPOPS = {
            AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO};

    private Context mContext;
    private ContentResolver mContentResolver;

    private Uri mExternalImages;

    @Parameter(0)
    public String mVolumeName;

    @Parameters
    public static Iterable<? extends Object> data() {
        return MediaProviderTestUtils.getSharedVolumeNames();
    }

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();

        Log.d(TAG, "Using volume " + mVolumeName + " for user " + mContext.getUserId());
        mExternalImages = MediaStore.Images.Media.getContentUri(mVolumeName);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Sure this is pointless, but czars demand test coverage.
     */
    @Test
    public void testConstructors() {
        new MediaStore();
        new MediaStore.Audio();
        new MediaStore.Audio.Albums();
        new MediaStore.Audio.Artists();
        new MediaStore.Audio.Artists.Albums();
        new MediaStore.Audio.Genres();
        new MediaStore.Audio.Genres.Members();
        new MediaStore.Audio.Media();
        new MediaStore.Audio.Playlists();
        new MediaStore.Audio.Playlists.Members();
        new MediaStore.Files();
        new MediaStore.Images();
        new MediaStore.Images.Media();
        new MediaStore.Images.Thumbnails();
        new MediaStore.Video();
        new MediaStore.Video.Media();
        new MediaStore.Video.Thumbnails();
    }

    @Test
    public void testRequireOriginal() {
        assertFalse(MediaStore.getRequireOriginal(mExternalImages));
        assertTrue(MediaStore.getRequireOriginal(MediaStore.setRequireOriginal(mExternalImages)));
    }

    @Test
    public void testGetMediaScannerUri() {
        // query
        Cursor c = mContentResolver.query(MediaStore.getMediaScannerUri(), null,
                null, null, null);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void testGetVersion() {
        // We should have valid versions to help detect data wipes
        assertNotNull(MediaStore.getVersion(getContext()));
        assertNotNull(MediaStore.getVersion(getContext(), MediaStore.VOLUME_INTERNAL));
        assertNotNull(MediaStore.getVersion(getContext(), MediaStore.VOLUME_EXTERNAL));
        assertNotNull(MediaStore.getVersion(getContext(), MediaStore.VOLUME_EXTERNAL_PRIMARY));
    }

    @Test
    public void testGetExternalVolumeNames() {
        Set<String> volumeNames = MediaStore.getExternalVolumeNames(getContext());

        assertFalse(volumeNames.contains(MediaStore.VOLUME_INTERNAL));
        assertFalse(volumeNames.contains(MediaStore.VOLUME_EXTERNAL));
        assertTrue(volumeNames.contains(MediaStore.VOLUME_EXTERNAL_PRIMARY));
    }

    @Test
    public void testGetRecentExternalVolumeNames() {
        Set<String> volumeNames = MediaStore.getRecentExternalVolumeNames(getContext());

        assertFalse(volumeNames.contains(MediaStore.VOLUME_INTERNAL));
        assertFalse(volumeNames.contains(MediaStore.VOLUME_EXTERNAL));
        assertTrue(volumeNames.contains(MediaStore.VOLUME_EXTERNAL_PRIMARY));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenFile() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);
        final CancellationSignal cg = new CancellationSignal();

        try (ParcelFileDescriptor pfd1 = mContext.getContentResolver()
                .openFileDescriptor(uri, "r", cg)) {
            try (ParcelFileDescriptor pfd2 = MediaStore
                    .openFileDescriptor(mContext.getContentResolver(), uri, "r", cg)) {
                long end1 = Os.lseek(pfd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(pfd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenAssetFile() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);
        final CancellationSignal cg = new CancellationSignal();

        try (AssetFileDescriptor afd1 = mContext.getContentResolver()
                .openAssetFileDescriptor(uri, "r", cg)) {
            try (AssetFileDescriptor afd2 = MediaStore
                    .openAssetFileDescriptor(mContext.getContentResolver(), uri, "r", cg)) {
                long end1 = Os.lseek(afd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(afd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenTypedAssetFile() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);
        final CancellationSignal cg = new CancellationSignal();

        try (AssetFileDescriptor afd1 = mContext.getContentResolver()
                .openTypedAssetFileDescriptor(uri, "*/*", null, cg)) {
            try (AssetFileDescriptor afd2 = MediaStore.openTypedAssetFileDescriptor(
                    mContext.getContentResolver(), uri, "*/*", null, cg)) {
                long end1 = Os.lseek(afd1.getFileDescriptor(), 0, OsConstants.SEEK_END);
                long end2 = Os.lseek(afd2.getFileDescriptor(), 0, OsConstants.SEEK_END);
                assertThat(end1).isEqualTo(end2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenFile_wrongAuthority() throws Exception {
        final Uri uri = Uri.parse("content://wrong_authority");

        try (ParcelFileDescriptor pfd = MediaStore
                .openFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenAssetFile_wrongAuthority() throws Exception {
        final Uri uri = Uri.parse("content://wrong_authority");

        try (AssetFileDescriptor afd = MediaStore
                .openAssetFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenTypedAssetFile_wrongAuthority() throws Exception {
        final Uri uri = Uri.parse("content://wrong_authority");

        try (AssetFileDescriptor afd = MediaStore.openTypedAssetFileDescriptor(
                mContext.getContentResolver(), uri, "*/*", null, null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenFile_wrongScheme() throws Exception {
        final Uri uri = Uri.parse("file://authority");

        try (ParcelFileDescriptor pfd = MediaStore
                .openFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenAssetFile_wrongScheme() throws Exception {
        final Uri uri = Uri.parse("file://authority");

        try (AssetFileDescriptor afd = MediaStore
                .openAssetFileDescriptor(mContext.getContentResolver(), uri, "r", null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_STORE_OPEN_FILE)
    public void testMediaStoreOpenTypedAssetFile_wrongScheme() throws Exception {
        final Uri uri = Uri.parse("file://authority");

        try (AssetFileDescriptor afd = MediaStore
                .openTypedAssetFileDescriptor(
                        mContext.getContentResolver(), uri, "*/*", null, null)) {
            fail("Expected IllegalArgumentException thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testGetStorageVolume() throws Exception {
        final Uri uri = MediaProviderTestUtils.stageMedia(R.raw.volantis, mExternalImages);

        final StorageManager sm = mContext.getSystemService(StorageManager.class);
        final StorageVolume sv = sm.getStorageVolume(uri);

        // We should always have a volume for media we just created
        assertNotNull(sv);

        if (MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(mVolumeName)) {
            assertEquals(sm.getPrimaryStorageVolume(), sv);
        }
    }

    @Test
    public void testGetStorageVolume_Unrelated() throws Exception {
        final StorageManager sm = mContext.getSystemService(StorageManager.class);
        try {
            sm.getStorageVolume(Uri.parse("content://com.example/path/to/item/"));
            fail("getStorageVolume unrelated should throw exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testRewriteToLegacy() throws Exception {
        final Uri before = MediaStore.Images.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        final Uri after = MediaStore.rewriteToLegacy(before);

        assertEquals(MediaStore.AUTHORITY, before.getAuthority());
        assertEquals(MediaStore.AUTHORITY_LEGACY, after.getAuthority());
    }

    /**
     * When upgrading from an older device, we really need our legacy provider
     * to be present to ensure that we don't lose user data like
     * {@link BaseColumns#_ID} and {@link MediaColumns#IS_FAVORITE}.
     */
    @Test
    public void testLegacy() throws Exception {
        final ProviderInfo legacy = getContext().getPackageManager()
                .resolveContentProvider(MediaStore.AUTHORITY_LEGACY, 0);
        if (legacy == null) {
            if (Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.R) {
                // If we're a brand new device, we don't require a legacy
                // provider, since there's nothing to upgrade
                return;
            } else {
                fail("Upgrading devices must have a legacy MediaProvider at "
                        + "MediaStore.AUTHORITY_LEGACY to upgrade user data from");
            }
        }

        // Verify that legacy provider is protected
        assertEquals("Legacy provider at MediaStore.AUTHORITY_LEGACY must protect its data",
                android.Manifest.permission.WRITE_MEDIA_STORAGE, legacy.readPermission);
        assertEquals("Legacy provider at MediaStore.AUTHORITY_LEGACY must protect its data",
                android.Manifest.permission.WRITE_MEDIA_STORAGE, legacy.writePermission);

        // And finally verify that legacy provider is headless
        final PackageInfo legacyPackage = getContext().getPackageManager().getPackageInfo(
                legacy.packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES);
        assertEmpty("Headless legacy MediaProvider must have no activities",
                legacyPackage.activities);
        assertEquals("Headless legacy MediaProvider must have exactly one provider",
                1, legacyPackage.providers.length);
        assertEmpty("Headless legacy MediaProvider must have no receivers",
                legacyPackage.receivers);
        assertEmpty("Headless legacy MediaProvider must have no services",
                legacyPackage.services);
    }

    @Test
    public void testIsCurrentSystemGallery() throws Exception {
        assertThat(
                MediaStore.isCurrentSystemGallery(
                        mContentResolver, Process.myUid(), getContext().getPackageName()))
                .isFalse();

        try {
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ALLOWED, SYSTEM_GALERY_APPOPS);
            assertThat(
                    MediaStore.isCurrentSystemGallery(
                            mContentResolver, Process.myUid(), getContext().getPackageName()))
                    .isTrue();
        } finally {
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ERRORED, SYSTEM_GALERY_APPOPS);
        }

        assertThat(
                MediaStore.isCurrentSystemGallery(
                        mContentResolver, Process.myUid(), getContext().getPackageName()))
                .isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testCanManageMedia() throws Exception {
        final String opString = AppOpsManager.permissionToOp(Manifest.permission.MANAGE_MEDIA);

        // no access
        assertThat(MediaStore.canManageMedia(getContext())).isFalse();
        try {
            // grant access
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ALLOWED, opString);

            assertThat(MediaStore.canManageMedia(getContext())).isTrue();
        } finally {
            setAppOpsModeForUid(Process.myUid(), AppOpsManager.MODE_ERRORED, opString);
        }
        // no access
        assertThat(MediaStore.canManageMedia(getContext())).isFalse();
    }

    private void setAppOpsModeForUid(int uid, int mode, @NonNull String... ops) {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(null);
        try {
            for (String op : ops) {
                getContext().getSystemService(AppOpsManager.class).setUidMode(op, uid, mode);
            }
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private static <T> void assertEmpty(String message, T[] array) {
        if (array != null && array.length > 0) {
            fail(message);
        }
    }
}
