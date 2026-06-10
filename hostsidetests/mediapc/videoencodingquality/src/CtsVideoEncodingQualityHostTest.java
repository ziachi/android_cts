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

package android.videoencodingquality.cts;

import static com.android.media.videoquality.bdrate.BdRateMain.verifyBdRate;

import android.cts.host.utils.DeviceJUnit4ClassRunnerWithParameters;
import android.cts.host.utils.DeviceJUnit4Parameterized;
import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.util.CddTest;
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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

/**
 * This class constitutes host-part of video encoding quality test (go/pc14-veq). This test is
 * aimed towards benchmarking encoders on the target device.
 * <p>
 * Video encoding quality test quantifies encoders on the test device by encoding a set of clips
 * at various configurations. The encoded output is analysed for vmaf and compared against
 * reference. This entire process is not carried on the device. The host side of the test
 * prepares the test environment by installing a VideoEncodingApp on the device. It also pushes
 * the test vectors and test configurations on to the device. The VideoEncodingApp transcodes the
 * input clips basing on the configurations shared. The host side of the test then pulls output
 * files from the device and analyses for vmaf. These values are compared against reference using
 * Bjontegaard metric.
 **/
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
@OptionClass(alias = "pc-veq-test")
public class CtsVideoEncodingQualityHostTest implements IDeviceTest {
    private static final String RES_URL =
            "https://storage.googleapis.com/android_media/cts/hostsidetests/pc14_veq/veqtests-1_4.tar.gz";

    // variables related to host-side of the test
    private static final int MEDIA_PERFORMANCE_CLASS_14 = 34;
    private static final int MINIMUM_VALID_SDK = 31;
            // test is not valid before sdk 31, aka Android 12, aka Android S

    private static final Lock sLock = new ReentrantLock();
    private static final Condition sCondition = sLock.newCondition();
    private static boolean sIsTestSetUpDone = false;
            // install apk, push necessary resources to device to run the test. lock/condition
            // pair is to keep setupTestEnv() thread safe
    private static File sHostWorkDir;
    private static int sMpc;

    // Variables related to device-side of the test. These need to kept in sync with definitions of
    // VideoEncodingApp.apk
    private static final String DEVICE_SIDE_TEST_PACKAGE = "android.videoencoding.app";
    private static final String DEVICE_SIDE_TEST_CLASS =
            "android.videoencoding.app.VideoTranscoderTest";
    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";
    private static final String TEST_CONFIG_INST_ARGS_KEY = "conf-json";
    private static final long DEFAULT_SHELL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final String TEST_TIMEOUT_INST_ARGS_KEY = "timeout_msec";
    private static final long DEFAULT_TEST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3);

    // local variables related to host-side of the test
    private final String mJsonName;
    private ITestDevice mDevice;

    @Option(name = "force-to-run", description = "Force to run the test even if the device is not"
            + " a right performance class device.")
    private boolean mForceToRun = false;

    @Option(name = "skip-avc", description = "Skip avc encoder testing")
    private boolean mSkipAvc = false;

    @Option(name = "skip-hevc", description = "Skip hevc encoder testing")
    private boolean mSkipHevc = false;

    @Option(name = "skip-p", description = "Skip P only testing")
    private boolean mSkipP = false;

    @Option(name = "skip-b", description = "Skip B frame testing")
    private boolean mSkipB = false;

    @Option(name = "reset", description = "Start with a fresh directory.")
    private boolean mReset = false;

    @Option(name = "quick-check", description = "Run a quick check.")
    private boolean mQuickCheck = false;

    public CtsVideoEncodingQualityHostTest(String jsonName,
            @SuppressWarnings("unused") String testLabel) {
        mJsonName = jsonName;
    }

    private static final List<Object[]> AVC_VBR_B0_PARAMS = Arrays.asList(new Object[][]{
            {"AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
                    "Beach_SO04_CRW02_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b0"},
            {"AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0"
                    + ".json",
                    "BirthdayHalfway_SI17_CRUW03_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b0"},
            {"AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
                    "SelfieTeenKitchenSocialMedia_SS01_CF01_P_420_8bit_SDR_1080p_30fps_hw_avc_"
                            + "vbr_b0"},
            {"AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
                    "Waterfall_SO05_CRW01_P_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b0"},
            {"AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json"
                    , "SelfieFamily_SF14_CF01_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b0"},
            {"AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
                    "River_SO03_CRW01_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b0"},
            {"AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0"
                    + ".json",
                    "SelfieGroupGarden_SF15_CF01_P_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b0"},
            {"AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json"
                    , "ConcertNear_SI10_CRW01_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b0"},
            {"AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR"
                    + "-1080p-30fps_hw_avc_vbr_b0.json",
                    "SelfieCoupleCitySocialMedia_SS02_CF01_P_420_8bit_SDR_1080p_30fps_hw_avc_"
                            + "vbr_b0"}});

    private static final List<Object[]> AVC_VBR_B3_PARAMS = Arrays.asList(new Object[][]{
            {"AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
                    "Beach_SO04_CRW02_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b3"},
            {"AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3"
                    + ".json",
                    "BirthdayHalfway_SI17_CRUW03_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b3"},
            {"AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b3.json",
                    "SelfieTeenKitchenSocialMedia_SS01_CF01_P_420_8bit_SDR_1080p_30fps_hw_avc_"
                            + "vbr_b3"},
            {"AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
                    "Waterfall_SO05_CRW01_P_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b3"},
            {"AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json"
                    , "SelfieFamily_SF14_CF01_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b3"},
            {"AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
                    "River_SO03_CRW01_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b3"},
            {"AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3"
                    + ".json",
                    "SelfieGroupGarden_SF15_CF01_P_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b3"},
            {"AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json"
                    , "ConcertNear_SI10_CRW01_L_420_8bit_SDR_1080p_30fps_hw_avc_vbr_b3"},
            {"AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR"
                    + "-1080p-30fps_hw_avc_vbr_b3.json",
                    "SelfieCoupleCitySocialMedia_SS02_CF01_P_420_8bit_SDR_1080p_30fps_hw_avc_"
                            + "vbr_b3"}});

    private static final List<Object[]> HEVC_VBR_B0_PARAMS = Arrays.asList(new Object[][]{
            {"AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
                    "Beach_SO04_CRW02_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b0"},
            {"AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0"
                    + ".json",
                    "BirthdayHalfway_SI17_CRUW03_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b0"},
            {"AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json",
                    "SelfieTeenKitchenSocialMedia_SS01_CF01_P_420_8bit_SDR_1080p_30fps_hw_hevc_"
                            + "vbr_b0"},
            {"AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
                    "Waterfall_SO05_CRW01_P_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b0"},
            {"AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json"
                    , "SelfieFamily_SF14_CF01_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b0"},
            {"AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
                    "River_SO03_CRW01_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b0"},
            {"AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0"
                    + ".json",
                    "SelfieGroupGarden_SF15_CF01_P_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b0"},
            {"AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json"
                    , "ConcertNear_SI10_CRW01_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b0"},
            {"AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR"
                    + "-1080p-30fps_hw_hevc_vbr_b0.json",
                    "SelfieCoupleCitySocialMedia_SS02_CF01_P_420_8bit_SDR_1080p_30fps_hw_hevc_"
                            + "vbr_b0"}});

    private static final List<Object[]> HEVC_VBR_B3_PARAMS = Arrays.asList(new Object[][]{
            {"AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
                    "Beach_SO04_CRW02_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b3"},
            {"AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3"
                    + ".json",
                    "BirthdayHalfway_SI17_CRUW03_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b3"},
            {"AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b3.json",
                    "SelfieTeenKitchenSocialMedia_SS01_CF01_P_420_8bit_SDR_1080p_30fps_hw_hevc_"
                            + "vbr_b3"},
            {"AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
                    "Waterfall_SO05_CRW01_P_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b3"},
            {"AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json"
                    , "SelfieFamily_SF14_CF01_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b3"},
            {"AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
                    "River_SO03_CRW01_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b3"},
            // Abnormal curve, not monotonically increasing.
            /*{"AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3"
                    + ".json",
                    "SelfieGroupGarden_SF15_CF01_P_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b3"},*/
            {"AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json"
                    , "ConcertNear_SI10_CRW01_L_420_8bit_SDR_1080p_30fps_hw_hevc_vbr_b3"},
            {"AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR"
                    + "-1080p-30fps_hw_hevc_vbr_b3.json",
                    "SelfieCoupleCitySocialMedia_SS02_CF01_P_420_8bit_SDR_1080p_30fps_hw_hevc_"
                            + "vbr_b3"}});

    private static final List<Object[]> QUICK_RUN_PARAMS = Arrays.asList(new Object[][]{
            {"AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
                    "SelfieTeenKitchenSocialMedia_SS01_CF01_P_420_8bit_SDR_1080p_30fps_hw_avc_" +
                            "vbr_b0"},
            {"AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json",
                    "SelfieTeenKitchenSocialMedia_SS01_CF01_P_420_8bit_SDR_1080p_30fps_hw_hevc_"
                            + "vbr_b0"}});

    @Parameterized.Parameters(name = "{index}_{1}")
    public static List<Object[]> input() {
        final List<Object[]> args = new ArrayList<>();
        args.addAll(AVC_VBR_B0_PARAMS);
        args.addAll(AVC_VBR_B3_PARAMS);
        args.addAll(HEVC_VBR_B0_PARAMS);
        args.addAll(HEVC_VBR_B3_PARAMS);
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

        String pcAsString = getDevice().getProperty("ro.odm.build.media_performance_class");
        try {
            sMpc = Integer.parseInt("0" + pcAsString);
        } catch (Exception e) {
            LogUtil.CLog.i("Invalid pcAsString: " + pcAsString + ", exception: " + e);
        }

        Assume.assumeTrue("Performance class advertised by the test device is less than "
                + MEDIA_PERFORMANCE_CLASS_14, mForceToRun || sMpc >= MEDIA_PERFORMANCE_CLASS_14
                || (sMpc == 0 && sdk >= 34 /* Build.VERSION_CODES.UPSIDE_DOWN_CAKE */));

        Assert.assertTrue("Failed to install package on device : " + DEVICE_SIDE_TEST_PACKAGE,
                getDevice().isPackageInstalled(DEVICE_SIDE_TEST_PACKAGE));

        // set up host-side working directory
        String tmpBase = System.getProperty("java.io.tmpdir");
        String dirName = "CtsVideoEncodingQualityHostTest_" + getDevice().getSerialNumber();
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
                + "/veq/input/";
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

    public static boolean containsJson(String jsonName, List<Object[]> params) {
        for (Object[] param : params) {
            if (param[0].equals(jsonName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verify the video encoding quality requirements for the performance class 14 devices.
     */
    @CddTest(requirements = {"2.2.7.1/5.8/H-1-1"})
    @Test
    public void testEncoding() throws Exception {
        Assume.assumeFalse("Skipping due to quick run mode",
                mQuickCheck && !containsJson(mJsonName, QUICK_RUN_PARAMS));
        Assume.assumeFalse("Skipping avc encoder tests",
                mSkipAvc && (containsJson(mJsonName, AVC_VBR_B0_PARAMS) || containsJson(mJsonName,
                        AVC_VBR_B3_PARAMS)));
        Assume.assumeFalse("Skipping hevc encoder tests",
                mSkipHevc && (containsJson(mJsonName, HEVC_VBR_B0_PARAMS) || containsJson(mJsonName,
                        HEVC_VBR_B3_PARAMS)));
        Assume.assumeFalse("Skipping b-frame tests",
                mSkipB && (containsJson(mJsonName, AVC_VBR_B3_PARAMS) || containsJson(mJsonName,
                        HEVC_VBR_B3_PARAMS)));
        Assume.assumeFalse("Skipping non b-frame tests",
                mSkipP && (containsJson(mJsonName, AVC_VBR_B0_PARAMS) || containsJson(mJsonName,
                        HEVC_VBR_B0_PARAMS)));

        // set up test environment
        sLock.lock();
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
        String outDevPath = getDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE) + "/veq/output/"
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
        int fps = obj.getInt("FrameRate");
        int frameCount = obj.getInt("FrameCount");
        int clipDuration = frameCount / fps;

        // Compute Vmaf
        try (FileWriter writer = new FileWriter(outHostPath.getPath() + "/" + "all_vmafs.txt")) {
            JSONArray codecConfigs = obj.getJSONArray("CodecConfigs");
            int th = Runtime.getRuntime().availableProcessors() / 2;
            th = Math.min(Math.max(1, th), 8);
            String filter =
                    "[0:v]setpts=PTS-STARTPTS[reference];[1:v]setpts=PTS-STARTPTS[distorted];"
                            + "[distorted][reference]libvmaf=feature=name=psnr:model=version"
                            + "=vmaf_v0.6.1:n_threads=" + th;
            for (int i = 0; i < codecConfigs.length(); i++) {
                JSONObject codecConfig = codecConfigs.getJSONObject(i);
                String outputName = codecConfig.getString("EncodedFileName");
                outputName = outputName.substring(0, outputName.lastIndexOf("."));
                String outputVmafPath = outDir + "/" + outputName + ".txt";
                String cmd = "./bin/ffmpeg";
                cmd += " -hide_banner";
                cmd += " -r " + fps;
                cmd += " -i " + "samples/" + refFileName + " -an"; // reference video
                cmd += " -r " + fps;
                cmd += " -i " + outDir + "/" + outputName + ".mp4" + " -an"; // distorted video
                cmd += " -filter_complex " + "\"" + filter + "\"";
                cmd += " -f null -";
                cmd += " > " + outputVmafPath + " 2>&1";
                LogUtil.CLog.i("ffmpeg command : " + cmd);
                int result = runCommand(cmd, sHostWorkDir);
                if (sMpc >= MEDIA_PERFORMANCE_CLASS_14) {
                    Assert.assertEquals("Encountered error during vmaf computation.", 0, result);
                } else {
                    Assume.assumeTrue("Encountered error during vmaf computation but the "
                            + "test device does not advertise performance class", result == 0);
                }
                String vmafLine = "";
                try (BufferedReader reader = new BufferedReader(
                        new FileReader(sHostWorkDir.getPath() + "/" + outputVmafPath))) {
                    String token = "VMAF score: ";
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(token)) {
                            line = line.substring(line.indexOf(token));
                            vmafLine = "VMAF score = " + line.substring(token.length());
                            LogUtil.CLog.i(vmafLine);
                            break;
                        }
                    }
                } catch (IOException e) {
                    throw new AssertionError("Unexpected IOException: " + e.getMessage());
                }

                writer.write(vmafLine + "\n");
                writer.write("Y4M file = " + refFileName + "\n");
                writer.write("MP4 file = " + refFileName + "\n");
                File file = new File(outHostPath + "/" + outputName + ".mp4");
                Assert.assertTrue("output file from device missing", file.exists());
                long fileSize = file.length();
                writer.write("Filesize = " + fileSize + "\n");
                writer.write("FPS = " + fps + "\n");
                writer.write("FRAME_COUNT = " + frameCount + "\n");
                writer.write("CLIP_DURATION = " + clipDuration + "\n");
                long totalBits = fileSize * 8;
                long totalBits_kbps = totalBits / 1000;
                long bitrate_kbps = totalBits_kbps / clipDuration;
                writer.write("Bitrate kbps = " + bitrate_kbps + "\n");
            }
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException: " + e.getMessage());
        }

        // bd rate verification
        String refJsonFilePath = sHostWorkDir.getPath() + "/json/" + mJsonName;
        String testVmafFilePath = sHostWorkDir.getPath() + "/" + outDir + "/" + "all_vmafs.txt";
        String resultFilePath = sHostWorkDir.getPath() + "/" + outDir + "/result.txt";
        int result = verifyBdRate(refJsonFilePath, testVmafFilePath, resultFilePath);
        if (sMpc >= MEDIA_PERFORMANCE_CLASS_14) {
            Assert.assertEquals("bd rate validation failed.", 0, result);
        } else {
            Assume.assumeTrue("bd rate validation failed but the test device does not "
                    + "advertise performance class", result == 0);
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
