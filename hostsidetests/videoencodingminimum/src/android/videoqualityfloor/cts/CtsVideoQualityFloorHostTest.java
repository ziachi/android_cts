/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.videoqualityfloor.cts;

import android.cts.host.utils.DeviceJUnit4ClassRunnerWithParameters;
import android.cts.host.utils.DeviceJUnit4Parameterized;
import android.cts.statsdatom.lib.DeviceUtils;
import android.platform.test.annotations.AppModeFull;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.IDeviceTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
@OptionClass(alias = "pc-veq-test")
public class CtsVideoQualityFloorHostTest implements IDeviceTest {
    private static final String RES_URL =
            "https://storage.googleapis.com/android_media/cts/hostsidetests/videoqualityfloor/tests-1.1.tar.gz";

    // variables related to host-side of the test
    private static final int MINIMUM_VALID_SDK = 31;
            // test is not valid before sdk 31, aka Android 12, aka Android S
    private static final float TARGET_VMAF_SCORE = 70.0f;
    private static final float TOLERANCE = 0.95f;

    private static final Lock sLock = new ReentrantLock();
    private static final Condition sCondition = sLock.newCondition();
    private static boolean sIsTestSetUpDone = false;
            // install apk, push necessary resources to device to run the test. lock/condition
            // pair is to keep setupTestEnv() thread safe
    private static File sHostWorkDir;

    // Variables related to device-side of the test. These need to kept in sync with definitions of
    // VideoEncodingMinApp.apk
    private static final String DEVICE_SIDE_TEST_PACKAGE = "android.videoencodingmin.app";
    private static final String DEVICE_SIDE_TEST_CLASS =
            "android.videoencodingmin.app.VideoTranscoderTest";
    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";
    private static final String TEST_CONFIG_INST_ARGS_KEY = "conf-json";
    private static final long DEFAULT_SHELL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final String TEST_TIMEOUT_INST_ARGS_KEY = "timeout_msec";
    private static final long DEFAULT_TEST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3);

    // Constants from `android.content.pm.PackageManager`.
    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    // local variables related to host-side of the test
    private final String mJsonName;
    private ITestDevice mDevice;

    @Option(name = "reset", description = "Start with a fresh directory.")
    private boolean mReset = false;

    public CtsVideoQualityFloorHostTest(String jsonName,
            @SuppressWarnings("unused") String testLabel) {
        mJsonName = jsonName;
    }

    @Parameterized.Parameters(name = "{index}_{1}")
    public static List<Object[]> input() {
        final List<Object[]> args = new ArrayList<>();
        String[] clips = {"Fireworks", "MountainBike", "Motorcycle", "TreesAndGrass"};
        String[] resolutions = {"1080p", "720p", "540p", "480p"};
        String[] codecInfos = {"avcBaseline3", "avcHigh4", "avcHigh52", "hevcMain3"};

        for (String clip : clips) {
            for (String res : resolutions) {
                for (String info : codecInfos) {
                    Object[] testArgs = new Object[2];
                    testArgs[0] = res + "-" + clip + "-" + info + ".json";
                    testArgs[1] = res + "_" + clip + "_" + info;
                    args.add(testArgs);
                }
            }
        }
        return args;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Sets up the necessary environment for the video encoding quality test.
     */
    public void setupTestEnv() throws Exception {
        String sdkAsString = getDevice().getProperty("ro.build.version.sdk");
        int sdk = Integer.parseInt(sdkAsString);
        Assume.assumeTrue("Test requires sdk >= " + MINIMUM_VALID_SDK
                + " test device has sdk = " + sdk, sdk >= MINIMUM_VALID_SDK);

        Assert.assertTrue("Failed to install package on device : " + DEVICE_SIDE_TEST_PACKAGE,
                getDevice().isPackageInstalled(DEVICE_SIDE_TEST_PACKAGE));

        // set up host-side working directory
        String tmpBase = System.getProperty("java.io.tmpdir");
        String dirName = "CtsVideoQualityFloorHostTest_" + getDevice().getSerialNumber();
        String tmpDir = tmpBase + "/" + dirName;
        LogUtil.CLog.i("tmpBase= " + tmpBase + " tmpDir =" + tmpDir);
        sHostWorkDir = new File(tmpDir);
        if (mReset || sHostWorkDir.isFile()) {
            File cwd = new File(".");
            runCommand("rm -rf " + tmpDir, cwd);
        }
        try {
            if (!sHostWorkDir.isDirectory()) {
                Assert.assertTrue("Failed to create directory : " + sHostWorkDir.getAbsolutePath(),
                        sHostWorkDir.mkdirs());
            }
        } catch (SecurityException e) {
            LogUtil.CLog.e("Unable to establish temp directory " + sHostWorkDir.getPath());
        }

        // Clean up output folders before starting the test
        runCommand("rm -rf " + "output_*", sHostWorkDir);

        // Download the test suite tar file.
        downloadFile(RES_URL, sHostWorkDir);

        // Unpack the test suite tar file.
        String fileName = RES_URL.substring(RES_URL.lastIndexOf('/') + 1);
        int result = runCommand("tar xvzf " + fileName, sHostWorkDir);
        Assert.assertEquals("Failed to untar " + fileName, 0, result);

        // Push input files to device
        String deviceInDir = getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)
                + "/vqf/input/";
        String deviceJsonDir = deviceInDir + "json/";
        String deviceSamplesDir = deviceInDir + "samples/";
        Assert.assertNotNull("Failed to create directory " + deviceJsonDir + " on device ",
                getDevice().executeAdbCommand("shell", "mkdir", "-p", deviceJsonDir));
        Assert.assertNotNull("Failed to create directory " + deviceSamplesDir + " on device ",
                getDevice().executeAdbCommand("shell", "mkdir", "-p", deviceSamplesDir));
        Assert.assertTrue("Failed to push json files to " + deviceJsonDir + " on device ",
                getDevice().pushDir(new File(sHostWorkDir.getPath() + "/json/"), deviceJsonDir));
        Assert.assertTrue("Failed to push mp4 files to " + deviceSamplesDir + " on device ",
                getDevice().pushDir(new File(sHostWorkDir.getPath() + "/samples/"),
                        deviceSamplesDir));
        sIsTestSetUpDone = true;
    }

    /**
     * Verify the video encoding quality requirements for the devices running Android 12/S or above.
     */
    @Test
    public void testEncoding() throws Exception {
        // set up test environment
        sLock.lock();
        Assume.assumeTrue("Test is only valid for handheld devices", isDeviceHandheld());
        try {
            if (!sIsTestSetUpDone) setupTestEnv();
            sCondition.signalAll();
        } finally {
            sLock.unlock();
        }

        // transcode input
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, DEVICE_SIDE_TEST_CLASS, "testTranscode");

        // copy the encoded output from the device to the host.
        String outDir = "output_" + mJsonName.substring(0, mJsonName.indexOf('.'));
        File outHostPath = new File(sHostWorkDir, outDir);
        try {
            if (!outHostPath.isDirectory()) {
                Assert.assertTrue("Failed to create directory : " + outHostPath.getAbsolutePath(),
                        outHostPath.mkdirs());
            }
        } catch (SecurityException e) {
            LogUtil.CLog.e("Unable to establish output host directory : " + outHostPath.getPath());
        }
        String outDevPath = getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE) + "/vqf/output/"
                + outDir;
        Assert.assertTrue("Failed to pull mp4 files from " + outDevPath
                + " to " + outHostPath.getPath(), getDevice().pullDir(outDevPath, outHostPath));
        getDevice().deleteFile(outDevPath);

        // Parse json file
        String jsonPath = sHostWorkDir.getPath() + "/json/" + mJsonName;
        String jsonString =
                new String(Files.readAllBytes(Paths.get(jsonPath)), StandardCharsets.UTF_8);
        JSONArray jsonArray = new JSONArray(jsonString);
        JSONObject obj = jsonArray.getJSONObject(0);
        String refFileName = obj.getString("RefFileName");

        // Compute Vmaf
        JSONArray codecConfigs = obj.getJSONArray("CodecConfigs");
        int th = Runtime.getRuntime().availableProcessors() / 2;
        th = Math.min(Math.max(1, th), 8);
        String filter = "feature=name=psnr:model=version=vmaf_v0.6.1\\\\:enable_transform=true"
                + ":n_threads=" + th;
        for (int i = 0; i < codecConfigs.length(); i++) {
            JSONObject codecConfig = codecConfigs.getJSONObject(i);
            String outputName = codecConfig.getString("EncodedFileName");
            outputName = outputName.substring(0, outputName.lastIndexOf("."));
            String outputVmafPath = outDir + "/" + outputName + ".txt";
            String cmd = "./bin/ffmpeg";
            cmd += " -hide_banner";
            cmd += " -i " + outDir + "/" + outputName + ".mp4" + " -an";
            cmd += " -i " + "samples/" + refFileName + " -an";
            cmd += " -lavfi libvmaf=" + "\'" + filter + "\'";
            cmd += " -f null -";
            cmd += " > " + outputVmafPath + " 2>&1";
            LogUtil.CLog.i("ffmpeg command : " + cmd);
            int result = runCommand(cmd, sHostWorkDir);
            Assert.assertEquals("Encountered error during vmaf computation.", 0, result);

            String vmafLine = "";
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(sHostWorkDir.getPath() + "/" + outputVmafPath))) {
                String token = "VMAF score: ";
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(token)) {
                        line = line.substring(line.indexOf(token));
                        double vmaf_score = Double.parseDouble(line.substring(token.length()));
                        Assert.assertTrue(
                                "Video encoding failed for " + outputName + " with vmaf score of "
                                        + vmaf_score, vmaf_score >= TARGET_VMAF_SCORE * TOLERANCE);
                        LogUtil.CLog.i(vmafLine);
                        break;
                    }
                }
            } catch (IOException e) {
                throw new AssertionError("Unexpected IOException: " + e.getMessage());
            }
        }
        LogUtil.CLog.i("Finished executing the process.");
    }

    private int runCommand(String command, File dir) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("/bin/sh", "-c", command)
                .directory(dir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        String line;
        while ((line = stdInput.readLine()) != null || (line = stdError.readLine()) != null) {
            LogUtil.CLog.i(line + "\n");
        }
        return p.waitFor();
    }

    // Download the indicated file (within the base_url folder) to our desired destination
    // simple caching -- if file exists, we do not re-download
    private void downloadFile(String url, File destDir) {
        String fileName = url.substring(RES_URL.lastIndexOf('/') + 1);
        File destination = new File(destDir, fileName);

        // save bandwidth, also allows a user to manually preload files
        LogUtil.CLog.i("Do we already have a copy of file " + destination.getPath());
        if (destination.isFile()) {
            LogUtil.CLog.i("Skipping re-download of file " + destination.getPath());
            return;
        }

        String cmd = "wget -O " + destination.getPath() + " " + url;
        LogUtil.CLog.i("wget_cmd = " + cmd);

        int result = 0;
        try {
            result = runCommand(cmd, destDir);
        } catch (IOException e) {
            result = -2;
        } catch (InterruptedException e) {
            result = -3;
        }
        Assert.assertEquals("download file failed.\n", 0, result);
    }

    private void runDeviceTests(String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName) throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner = getTestRunner(pkgName, testClassName, testMethodName);
        CollectingTestListener listener = new CollectingTestListener();
        Assert.assertTrue(getDevice().runInstrumentationTests(testRunner, listener));
        assertTestsPassed(listener.getCurrentRunResults());
    }

    /** Checks if a device is handheld based on the description in CDD 2.2. */
    private boolean isDeviceHandheld() throws Exception {
        return DeviceUtils.hasFeature(getDevice(), FEATURE_TOUCHSCREEN)
                && !DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)
                && !DeviceUtils.hasFeature(getDevice(), FEATURE_LEANBACK)
                && !DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE);
    }

    private RemoteAndroidTestRunner getTestRunner(String pkgName, String testClassName,
            String testMethodName) {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }
        RemoteAndroidTestRunner testRunner =
                new RemoteAndroidTestRunner(pkgName, RUNNER, getDevice().getIDevice());
        testRunner.setMaxTimeToOutputResponse(DEFAULT_SHELL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        testRunner.addInstrumentationArg(TEST_TIMEOUT_INST_ARGS_KEY,
                Long.toString(DEFAULT_TEST_TIMEOUT_MILLIS));
        testRunner.addInstrumentationArg(TEST_CONFIG_INST_ARGS_KEY, mJsonName);
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }
        return testRunner;
    }

    private void assertTestsPassed(TestRunResult testRunResult) {
        if (testRunResult.isRunFailure()) {
            throw new AssertionError("Failed to successfully run device tests for "
                    + testRunResult.getName() + ": " + testRunResult.getRunFailureMessage());
        }
        if (testRunResult.getNumTests() != testRunResult.getPassedTests().size()) {
            for (Map.Entry<TestDescription, TestResult> resultEntry :
                    testRunResult.getTestResults().entrySet()) {
                if (resultEntry.getValue().getStatus().equals(TestStatus.FAILURE)) {
                    StringBuilder errorBuilder = new StringBuilder("On-device tests failed:\n");
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                    throw new AssertionError(errorBuilder.toString());
                }
                if (resultEntry.getValue().getStatus().equals(TestStatus.ASSUMPTION_FAILURE)) {
                    StringBuilder errorBuilder =
                            new StringBuilder("On-device tests assumption failed:\n");
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                    Assume.assumeTrue(errorBuilder.toString(), false);
                }
            }
        }
    }
}
