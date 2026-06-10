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

package android.provider.cts.media.modern;

import static android.provider.cts.ProviderTestUtils.executeShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.Timeout;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.io.BaseEncoding;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaProviderTestUtils {

    private static final String TAG = "MediaProviderTestUtils";
    private static final Timeout IO_TIMEOUT = new Timeout("IO_TIMEOUT", 2_000, 2, 2_000);
    private static final Pattern PATTERN_STORAGE_PATH = Pattern.compile(
            "(?i)^/storage/[^/]+/(?:[0-9]+/)?");

    private MediaProviderTestUtils() {
        // Utility class
    }

    public static Iterable<String> getSharedVolumeNames() {
        // We test both new and legacy volume names
        final HashSet<String> testVolumes = new HashSet<>();
        final Set<String> volumeNames = MediaStore.getExternalVolumeNames(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        // Run tests only on VISIBLE volumes which are FUSE mounted and indexed by MediaProvider
        for (String vol : volumeNames) {
            final File mountedPath = getVolumePath(vol);
            if (mountedPath == null || mountedPath.getAbsolutePath() == null) continue;
            if (mountedPath.getAbsolutePath().startsWith("/storage/")) {
                testVolumes.add(vol);
            }
        }
        testVolumes.add(MediaStore.VOLUME_EXTERNAL);
        return testVolumes;
    }

    public static String resolveVolumeName(String volumeName) {
        if (MediaStore.VOLUME_EXTERNAL.equals(volumeName)) {
            return MediaStore.VOLUME_EXTERNAL_PRIMARY;
        } else {
            return volumeName;
        }
    }

    public static void waitForIdle() {
        MediaStore.waitForIdle(InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getContentResolver());
    }

    /**
     * Waits until a file exists, or fails.
     *
     * @return existing file.
     */
    public static File waitUntilExists(File file) throws IOException {
        try {
            return IO_TIMEOUT.run("file '" + file + "' doesn't exist yet", () -> {
                return file.exists() ? file : null; // will retry if it returns null
            });
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static File getVolumePath(String volumeName) {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return context.getSystemService(StorageManager.class)
                .getStorageVolume(MediaStore.Files.getContentUri(volumeName)).getDirectory();
    }

    public static File stageDir(String volumeName) throws IOException {
        if (MediaStore.VOLUME_EXTERNAL.equals(volumeName)) {
            volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY;
        }
        final StorageVolume vol = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(StorageManager.class)
                .getStorageVolume(MediaStore.Files.getContentUri(volumeName));
        File dir = Environment.buildPath(vol.getDirectory(), "Android", "media",
                "android.provider.cts");
        Log.d(TAG, "stageDir(" + volumeName + "): returning " + dir);
        return dir;
    }

    public static File stageDownloadDir(String volumeName) throws IOException {
        if (MediaStore.VOLUME_EXTERNAL.equals(volumeName)) {
            volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY;
        }
        final StorageVolume vol = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(StorageManager.class)
                .getStorageVolume(MediaStore.Files.getContentUri(volumeName));
        return Environment.buildPath(vol.getDirectory(),
                Environment.DIRECTORY_DOWNLOADS);
    }

    public static File stageFile(int resId, File file) throws IOException {
        // The caller may be trying to stage into a location only available to
        // the shell user, so we need to perform the entire copy as the shell
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager.isSystemUser()
                && FileUtils.contains(Environment.getStorageDirectory(), file)) {
            executeShellCommand("mkdir -p " + file.getParent());
            waitUntilExists(file.getParentFile());
            try (AssetFileDescriptor afd = context.getResources().openRawResourceFd(resId)) {
                final File source = ParcelFileDescriptor.getFile(afd.getFileDescriptor());
                final long skip = afd.getStartOffset();
                final long count = afd.getLength();

                try {
                    // Try to create the file as calling package so that calling package remains
                    // as owner of the file.
                    file.createNewFile();
                } catch (IOException ignored) {
                    // Apps can't create files in other app's private directories, but shell can. If
                    // file creation fails, we ignore and let `dd` command create it instead.
                }

                executeShellCommand(String.format(
                        "dd bs=4K if=%s iflag=skip_bytes,count_bytes skip=%d count=%d of=%s",
                        source.getAbsolutePath(), skip, count, file.getAbsolutePath()));

                // Force sync to try updating other views
                executeShellCommand("sync");
            }
        } else {
            final File dir = file.getParentFile();
            dir.mkdirs();
            if (!dir.exists()) {
                throw new FileNotFoundException("Failed to create parent for " + file);
            }
            try (InputStream source = context.getResources().openRawResource(resId);
                    OutputStream target = new FileOutputStream(file)) {
                FileUtils.copy(source, target);
            }
        }
        return waitUntilExists(file);
    }

    public static File createMediaInDownloads(ContentResolver resolver, String volumeName)
            throws Exception {
        final File dir = new File(
                getVolumePath(resolveVolumeName(volumeName)), Environment.DIRECTORY_DOWNLOADS);
        final File file = new File(dir, System.nanoTime() + ".png");

        // Write 1 byte because 0 byte files are not valid in the db
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(1);
        }
        MediaStore.scanFile(resolver, file);

        // Sleep is needed for images to have different modified_date
        SystemClock.sleep(2000);

        return file;
    }

    public static Uri stageMedia(int resId, Uri collectionUri) throws IOException {
        return stageMedia(resId, collectionUri, "image/png");
    }

    public static Uri stageMedia(int resId, Uri collectionUri, String mimeType) throws IOException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String displayName = "cts" + System.nanoTime();
        final MediaStoreUtils.PendingParams params = new MediaStoreUtils.PendingParams(
                collectionUri, displayName, mimeType);
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (MediaStoreUtils.PendingSession session = MediaStoreUtils.openPending(context,
                pendingUri)) {
            try (InputStream source = context.getResources().openRawResource(resId);
                    OutputStream target = session.openOutputStream()) {
                FileUtils.copy(source, target);
            }
            return session.publish();
        }
    }

    public static Uri scanFile(File file) throws Exception {
        final Uri uri = MediaStore.scanFile(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getContentResolver(), file);
        assertWithMessage("no URI for '%s'", file).that(uri).isNotNull();
        return uri;
    }

    public static Uri scanFileFromShell(File file) throws Exception {
        return scanFile(file);
    }

    public static void setOwner(Uri uri, String packageName) throws Exception {
        executeShellCommand("content update"
                + " --user " + androidx.test.InstrumentationRegistry.getTargetContext().getUserId()
                + " --uri " + uri
                + " --bind owner_package_name:s:" + packageName);
    }

    public static void clearOwner(Uri uri) throws Exception {
        executeShellCommand("content update"
                + " --user " + androidx.test.InstrumentationRegistry.getTargetContext().getUserId()
                + " --uri " + uri
                + " --bind owner_package_name:n:");
    }

    public static byte[] hash(InputStream in) throws Exception {
        try (DigestInputStream digestIn = new DigestInputStream(in,
                MessageDigest.getInstance("SHA-1"));
                OutputStream out = new FileOutputStream(new File("/dev/null"))) {
            FileUtils.copy(digestIn, out);
            return digestIn.getMessageDigest().digest();
        }
    }

    /**
     * Extract the average overall color of the given bitmap.
     * <p>
     * Internally takes advantage of gaussian blurring that is naturally applied
     * when downscaling an image.
     */
    public static int extractAverageColor(Bitmap bitmap) {
        final Bitmap res = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(res);
        final Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final Rect dst = new Rect(0, 0, 1, 1);
        canvas.drawBitmap(bitmap, src, dst, null);
        return res.getPixel(0, 0);
    }

    public static void assertColorMostlyEquals(int expected, int actual) {
        assertTrue("Expected " + Integer.toHexString(expected) + " but was "
                + Integer.toHexString(actual), isColorMostlyEquals(expected, actual));
    }

    public static void assertColorMostlyNotEquals(int expected, int actual) {
        assertFalse("Expected " + Integer.toHexString(expected) + " but was "
                + Integer.toHexString(actual), isColorMostlyEquals(expected, actual));
    }

    private static boolean isColorMostlyEquals(int expected, int actual) {
        final float[] expectedHSV = new float[3];
        final float[] actualHSV = new float[3];
        Color.colorToHSV(expected, expectedHSV);
        Color.colorToHSV(actual, actualHSV);

        // Fail if more than a 10% difference in any component
        if (Math.abs(expectedHSV[0] - actualHSV[0]) > 36) return false;
        if (Math.abs(expectedHSV[1] - actualHSV[1]) > 0.1f) return false;
        if (Math.abs(expectedHSV[2] - actualHSV[2]) > 0.1f) return false;
        return true;
    }

    public static void assertExists(String path) throws IOException {
        assertExists(null, path);
    }

    public static void assertExists(File file) throws IOException {
        assertExists(null, file.getAbsolutePath());
    }

    public static void assertExists(String msg, String path) throws IOException {
        if (!access(path)) {
            if (msg != null) {
                fail(path + ": " + msg);
            } else {
                fail("File " + path + " does not exist");
            }
        }
    }

    public static void assertNotExists(String path) throws IOException {
        assertNotExists(null, path);
    }

    public static void assertNotExists(File file) throws IOException {
        assertNotExists(null, file.getAbsolutePath());
    }

    public static void assertNotExists(String msg, String path) throws IOException {
        if (access(path)) {
            fail(msg);
        }
    }

    private static boolean access(String path) throws IOException {
        // The caller may be trying to stage into a location only available to
        // the shell user, so we need to perform the entire copy as the shell
        if (FileUtils.contains(Environment.getStorageDirectory(), new File(path))) {
            return executeShellCommand("ls -la " + path).contains(path);
        } else {
            try {
                Os.access(path, OsConstants.F_OK);
                return true;
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.ENOENT) {
                    return false;
                } else {
                    throw new IOException(e.getMessage());
                }
            }
        }
    }

    public static boolean containsId(Uri uri, long id) {
        return containsId(uri, null, id);
    }

    public static boolean containsId(Uri uri, Bundle extras, long id) {
        try (Cursor c = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getContentResolver().query(uri,
                        new String[] { MediaStore.MediaColumns._ID }, extras, null)) {
            while (c.moveToNext()) {
                if (c.getLong(0) == id) return true;
            }
        }
        return false;
    }

    /**
     * Gets File corresponding to the uri.
     * This function assumes that the caller has access to the uri
     * @param uri uri to get File for
     * @return File file corresponding to the uri
     * @throws FileNotFoundException if either the file does not exist or the caller does not have
     * read access to the file
     */
    public static File getRawFile(Uri uri) throws Exception {
        String filePath;
        try (Cursor c = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getContentResolver()
                .query(uri, new String[] { MediaStore.MediaColumns.DATA }, null, null)) {
            assertTrue(c.moveToFirst());
            filePath = c.getString(0);
        }
        if (filePath != null) {
            return new File(filePath);
        } else {
            throw new FileNotFoundException("Failed to find _data for " + uri);
        }
    }

    public static String getRawFileHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
        }

        byte[] hash = digest.digest();
        return BaseEncoding.base16().encode(hash);
    }

    public static File getRelativeFile(Uri uri) throws Exception {
        final String path = getRawFile(uri).getAbsolutePath();
        final Matcher matcher = PATTERN_STORAGE_PATH.matcher(path);
        if (matcher.find()) {
            return new File(path.substring(matcher.end()));
        } else {
            throw new IllegalArgumentException();
        }
    }

    /** Revokes ACCESS_MEDIA_LOCATION from the test app */
    public static void revokeMediaLocationPermission(Context context) throws Exception {
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity("android.permission.MANAGE_APP_OPS_MODES",
                            "android.permission.REVOKE_RUNTIME_PERMISSIONS");

            // Revoking ACCESS_MEDIA_LOCATION permission will kill the test app.
            // Deny access_media_permission App op to revoke this permission.
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();
            if (packageManager.checkPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION,
                    packageName) == PackageManager.PERMISSION_GRANTED) {
                context.getPackageManager().updatePermissionFlags(
                        android.Manifest.permission.ACCESS_MEDIA_LOCATION, packageName,
                        PackageManager.FLAG_PERMISSION_REVOKED_COMPAT,
                        PackageManager.FLAG_PERMISSION_REVOKED_COMPAT, context.getUser());
                context.getSystemService(AppOpsManager.class).setUidMode(
                        "android:access_media_location", Process.myUid(),
                        AppOpsManager.MODE_IGNORED);
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /**
     * @return {@code true} if initial sdk version of the device is at least Android R
     */
    public static boolean isDeviceInitialSdkIntR() {
        // Build.VERSION.DEVICE_INITIAL_SDK_INT is available only Android S onwards
        int deviceInitialSdkInt = SdkLevel.isAtLeastS() ? Build.VERSION.DEVICE_INITIAL_SDK_INT :
                SystemProperties.getInt("ro.product.first_api_level", 0);
        return deviceInitialSdkInt >= Build.VERSION_CODES.R;
    }
}
