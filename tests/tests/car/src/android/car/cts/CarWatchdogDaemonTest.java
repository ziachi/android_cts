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

package android.car.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.UiAutomation;
import android.automotive.watchdog.CarWatchdogDaemonDump;
import android.automotive.watchdog.PackageStorageIoStats;
import android.automotive.watchdog.PerformanceProfilerDump;
import android.automotive.watchdog.PerformanceStats;
import android.automotive.watchdog.StatsCollection;
import android.automotive.watchdog.StatsRecord;
import android.automotive.watchdog.StorageIoStats;
import android.automotive.watchdog.UserPackageInfo;
import android.car.test.util.DiskUtils;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.system.Os;
import android.system.StructStatVfs;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class CarWatchdogDaemonTest extends AbstractCarTestCase {

    private static final String TAG = CarWatchdogDaemonTest.class.getSimpleName();

    // System event performance data collections are extended for at least 30 seconds after
    // receiving the corresponding system event completion notification. During these periods
    // (on <= Android T releases), a custom collection cannot be started. Thus, retry starting
    // custom collection for at least twice this duration.
    private static final long START_CUSTOM_COLLECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(60);
    private static final String START_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --start_perf --max_duration"
                    + " 120 --interval 5 --filter_packages " + getContext().getPackageName();
    private static final String STOP_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --stop_perf";
    private static final String DUMP_CUSTOM_PERF_COLLECTION_PROTO_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --proto";
    private static final String START_CUSTOM_COLLECTION_SUCCESS_MSG =
            "Successfully started custom perf collection";

    private static final int MAX_WRITE_BYTES = 100 * 1000;
    private static final int CAPTURE_WAIT_MS = 10 * 1000;

    private static final String VALUE_PERCENT_REGEX_PAIR = ",\\s(\\d+),\\s\\d+\\.\\d+%";
    private static final Pattern TOP_N_WRITES_LINE_PATTERN = Pattern.compile("(\\d+),\\s(\\S*)" +
            VALUE_PERCENT_REGEX_PAIR + VALUE_PERCENT_REGEX_PAIR + VALUE_PERCENT_REGEX_PAIR +
            VALUE_PERCENT_REGEX_PAIR);

    private File testDir;

    @Before
    public void setUp() throws IOException {
        File dataDir = getContext().getDataDir();
        testDir = Files.createTempDirectory(dataDir.toPath(),
                "CarWatchdogDaemon").toFile();
    }

    @After
    public void tearDown() {
        testDir.delete();
    }

    @Test
    public void testRecordsIoPerformanceData() throws Exception {
        startCustomPerformanceDataCollection();

        StructStatVfs stat;
        stat = Os.statvfs(testDir.getAbsolutePath());
        long limit = (long) (stat.f_bfree * stat.f_frsize * ((double) 2 / 3));
        long size = Math.min(MAX_WRITE_BYTES, limit);
        File file = new File(testDir, Long.toString(System.nanoTime()));
        file.createNewFile();
        long writtenBytes = DiskUtils.writeToDisk(file, size);
        assertWithMessage("Failed to write data to dir '" + testDir.getAbsolutePath() + "'").that(
                writtenBytes).isGreaterThan(0L);
        // Sleep twice the collection interval to capture the entire write.
        Thread.sleep(CAPTURE_WAIT_MS);
        CarWatchdogDaemonDump contents = stopCustomCollectionAndGetProtoDump();
        long recordedBytes = verifyProtoDumpAndGetWrittenBytesForPackage(contents,
                UserHandle.getUserId(Process.myUid()), getContext().getPackageName());
        assertThat(recordedBytes).isAtLeast(writtenBytes);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void startCustomPerformanceDataCollection() throws Exception {
        if (ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)) {
            String result = runShellCommand(START_CUSTOM_PERF_COLLECTION_CMD);
            assertWithMessage("Custom collection start message").that(result)
                    .contains(START_CUSTOM_COLLECTION_SUCCESS_MSG);
            return;
        }
        PollingCheck.check("Failed to start custom collect performance data collection",
                START_CUSTOM_COLLECTION_TIMEOUT_MS,
                () -> {
                    String result = runShellCommand(START_CUSTOM_PERF_COLLECTION_CMD);
                    return result.contains(START_CUSTOM_COLLECTION_SUCCESS_MSG) || result.isEmpty();
                });
    }


    /**
     * Executes a shell command.
     *
     * @return byte array.
     */
    public static byte[] executeShellCommand(String command) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();
        ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(command);
        try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] buf = new byte[512];
            int bytesRead;
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                outStream.write(buf, 0, bytesRead);
            }
            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop the custom collection and return the proto dump.
     *
     * @return Proto dump.
     */
    public static CarWatchdogDaemonDump stopCustomCollectionAndGetProtoDump() {
        byte[] contents = executeShellCommand(DUMP_CUSTOM_PERF_COLLECTION_PROTO_CMD);
        executeShellCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);
        assertWithMessage("Failed to custom collect I/O performance data").that(
                contents).isNotEmpty();
        try {
            CarWatchdogDaemonDump carWatchdogDaemonDump = CarWatchdogDaemonDump.parseFrom(contents);
            return carWatchdogDaemonDump;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse the custom I/O performance data dump generated by the carwatchdog daemon.
     *
     * @param contents      Content of the dump.
     * @param userId        UserId of the current process.
     * @param packageName   Package name of the current process.
     * @return Total written bytes recorded for the current userId and package name.
     */
    private static long verifyProtoDumpAndGetWrittenBytesForPackage(CarWatchdogDaemonDump contents,
            int userId, String packageName) throws Exception {
        long recordedBytes = 0;

        assertWithMessage("Performance profiler dump proto").that(
                contents.hasPerformanceProfilerDump()).isTrue();
        PerformanceProfilerDump performanceProfilerDump = contents.getPerformanceProfilerDump();

        assertWithMessage("Performance stats proto").that(
                performanceProfilerDump.hasPerformanceStats()).isTrue();
        PerformanceStats performanceStats = performanceProfilerDump.getPerformanceStats();

        assertWithMessage("Custom collection stats proto").that(
                performanceStats.hasCustomCollectionStats()).isTrue();
        StatsCollection custom_collection_stats = performanceStats.getCustomCollectionStats();

        List<StatsRecord> records = custom_collection_stats.getRecordsList();
        assertWithMessage("Size of records list").that(records.size()).isGreaterThan(0);

        for (StatsRecord record : records) {
            List<PackageStorageIoStats> packageStorageIoWriteStats =
                    record.getPackageStorageIoWriteStatsList();
            assertWithMessage("Size of package storage IO write stats list").that(
                    packageStorageIoWriteStats.size()).isGreaterThan(0);

            for (PackageStorageIoStats packageStorageIoWriteStat : packageStorageIoWriteStats) {
                assertWithMessage("User package info proto").that(
                        packageStorageIoWriteStat.hasUserPackageInfo()).isTrue();
                UserPackageInfo userPackageInfo = packageStorageIoWriteStat.getUserPackageInfo();

                assertWithMessage("User id").that(userPackageInfo.hasUserId()).isTrue();
                int recordUserId = userPackageInfo.getUserId();

                assertWithMessage("Package name").that(userPackageInfo.hasPackageName()).isTrue();
                String recordPackageName = userPackageInfo.getPackageName();

                if (recordUserId == userId && recordPackageName.equals(packageName)) {
                    assertWithMessage("Storage IO stats proto").that(
                            packageStorageIoWriteStat.hasStorageIoStats()).isTrue();
                    StorageIoStats storageIoStats = packageStorageIoWriteStat.getStorageIoStats();

                    assertWithMessage("Foreground bytes").that(
                            storageIoStats.hasFgBytes()).isTrue();
                    long foregroundBytes = storageIoStats.getFgBytes();

                    assertWithMessage("Background bytes").that(
                            storageIoStats.hasBgBytes()).isTrue();
                    long backgroundBytes = storageIoStats.getBgBytes();

                    recordedBytes += foregroundBytes;
                    recordedBytes += backgroundBytes;
                }
            }
        }

        return recordedBytes;
    }

    private enum Section {
        WRITTEN_BYTES_HEADER_SECTION,
        WRITTEN_BYTES_DATA_SECTION,
        NONE,
    }
}
