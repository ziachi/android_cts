/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Requirements.RoundTripAudioLatencyRequirement;
import android.mediapc.cts.common.Requirements.TwentyFourBitAudioRequirement;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils.UsbDeviceReport;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.AudioUtils;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.StatUtils;
import com.android.cts.verifier.audio.audiolib.WavFileCapture;
import com.android.cts.verifier.libs.ui.HtmlFormatter;
import com.android.cts.verifier.libs.ui.TextFormatter;

import org.hyphonate.megaaudio.common.Globals;
import org.hyphonate.megaaudio.common.StreamBase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.rules.TestName;

import java.io.File;
import java.util.Locale;

/**
 * CtsVerifier Audio Loopback Latency Test
 */
@CddTest(requirements = {"5.10/C-1-2,C-1-5", "5.6/H-1-2", "5.6/H-1-3"})
public class AudioLoopbackLatencyActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioLoopbackLatencyActivity";
    private static final boolean LOG = false;

    // JNI load
    static {
        try {
            System.loadLibrary("audioloopback_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading Audio Loopback JNI library");
            Log.e(TAG, "e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }

    Context mContext;
    protected AudioManager mAudioManager;

    private ConnectListener mConnectListener;

    // UI
    TextView[] mRouteStatus = new TextView[NUM_TEST_ROUTES];

    TextView mTestStatusText;
    ProgressBar mProgressBar;
    int mMaxLevel;

    TextView mTestInstructions;

    protected AudioLoopbackUtilitiesHandler mUtiltitiesHandler;

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();
    private Button[] mStartButtons = new Button[NUM_TEST_ROUTES];

    private WebView mResultsWebView;

    String mYesString;
    String mNoString;

    String mPassString;
    String mFailString;
    String mNotTestedString;
    String mRequiredString;
    String mNoHardwareString;
    String mUnknownHardwareString;

    // These flags determine the maximum allowed latency
    private boolean mClaimsProAudio;
    private boolean mClaimsLowLatency;
    private boolean mClaimsMediaPerformance;
    private boolean mClaimsOutput;
    private boolean mClaimsInput;

    // Useful info
    int mFirstProductApiLevel = SystemProperties.getInt("ro.product.first_api_level", -1);
    int mFirstBoardApiLevel = SystemProperties.getInt("ro.board.first_api_level", -1);
    private boolean mSupportsMMAP = AudioUtils.isMMapSupported();
    private boolean mSupportsMMAPExclusive = AudioUtils.isMMapExclusiveSupported();
    private boolean mOverallPass = false;

    private boolean mIsWatch;
    private boolean mIsTV;
    private boolean mIsAutomobile;
    private boolean mIsHandheld;
    private boolean mIsEmulator;
    private int     mSpeakerDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;
    private int     mMicDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;

    private int mUSBAudioSupport;
    private int mAnalogJackSupport;

    // Peripheral(s)
    private static final int NUM_TEST_ROUTES =       3;
    private static final int TESTROUTE_DEVICE =      0; // device speaker + mic
    private static final int TESTROUTE_ANALOG_JACK = 1;
    private static final int TESTROUTE_USB =         2;
    private int mTestRoute = TESTROUTE_DEVICE;

    // Loopback Logic
    private NativeAnalyzerThread mNativeAnalyzerThread = null;

    protected static final int NUM_TEST_PHASES = 5;
    protected int mTestPhase = 0;

    private static final double CONFIDENCE_THRESHOLD_AMBIENT = 0.6;
    private static final double CONFIDENCE_THRESHOLD_WIRED = 0.6;



    public static final double LATENCY_NOT_MEASURED = 0.0;
    public static final double LATENCY_BASIC = 200.0; // Was 300 in CDD 14 for UDC
                                                      // Was 250 in CDD 15 for VIC
    public static final double LATENCY_PRO_AUDIO_AT_LEAST_ONE = 25.0;
    public static final double LATENCY_PRO_AUDIO_ANALOG = 20.0;
    public static final double LATENCY_PRO_AUDIO_USB = 25.0;
    public static final double LATENCY_MPC_AT_LEAST_ONE = 80.0;

    public static final double TIMESTAMP_ACCURACY_MS = 30.0;

    // The audio stream callback threads should stop and close
    // in less than a few hundred msec. This is a generous timeout value.
    private static final int STOP_TEST_TIMEOUT_MSEC = 2 * 1000;

    private static final String LOG_ERROR_STR = "Could not log metric.";

    private TestSpec[] mTestSpecs = new TestSpec[NUM_TEST_ROUTES];
    private volatile UsbDeviceReport mUsbDeviceReport;

    final TestName mTestName = new TestName();

    // WAV File Stuff
    File mFilesDir;
    WavFileCapture mWavFileCapture;

    class TestSpec {
        private static final String TAG = "AudioLoopbackLatencyActivity.TestSpec";
        // impossibly low latencies (indicating something in the test went wrong).
        protected static final double LOWEST_REASONABLE_LATENCY_MILLIS = 1.0;

        final int mRouteId;

        // runtime assigned device ID
        static final int DEVICEID_NONE = -1;
        int mInputDeviceId;
        int mOutputDeviceId;

        String mDeviceName;

        double[] mLatencyMS = new double[NUM_TEST_PHASES];
        double[] mConfidence = new double[NUM_TEST_PHASES];

        double[] mTimestampLatencyMS = new double[NUM_TEST_PHASES];

        double mMeanLatencyMS;
        double mMeasuredLatencyMS;
        double mLatencyOffsetMS;
        double mMeanAbsoluteDeviation;
        double mMeanConfidence;
        double mRequiredConfidence;
        double mMeanTimestampLatencyMS;
        int mSampleRate;
        boolean mHas24BitHardwareSupport;
        int mHardwareFormat;

        // Stream Attributes
        int[] mBurstFrames = new int[NativeAnalyzerThread.NUM_STREAM_TYPES];
        int[] mCapacityFrames = new int[NativeAnalyzerThread.NUM_STREAM_TYPES];
        boolean[] mIsLowLatencyStream = new boolean[NativeAnalyzerThread.NUM_STREAM_TYPES];
        boolean[] mIsMMapStream = new boolean[NativeAnalyzerThread.NUM_STREAM_TYPES];

        boolean mRouteAvailable; // Have we seen this route/device at any time
        boolean mRouteConnected; // is the route available NOW
        boolean mTestRun;

        String mWavCaptureFileName;
        int mCaptureCode = mWavFileCapture.CAPTURE_NOTDONE;

        TestSpec(int routeId, double requiredConfidence) {
            mRouteId = routeId;
            mRequiredConfidence = requiredConfidence;

            mInputDeviceId = DEVICEID_NONE;
            mOutputDeviceId = DEVICEID_NONE;

            // Default to true if test not run.
            mHas24BitHardwareSupport = true;
        }

        void startTest() {
            mTestRun = true;

            java.util.Arrays.fill(mLatencyMS, 0.0);
            java.util.Arrays.fill(mConfidence, 0.0);
            java.util.Arrays.fill(mTimestampLatencyMS, 0.0);

            mTestStatusText.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);

            // WAV Capture
            mWavCaptureFileName = "AudioLoopbackTest_" + mRouteId + ".wav";
            mWavFileCapture.setCaptureFile(mFilesDir.getPath() + "/" + mWavCaptureFileName);
            mWavFileCapture.setWavSpec(/*numChannels*/ 1, mSampleRate);
            mWavFileCapture.startCapture();
        }

        void recordPhase(int phase, double latencyMS, double confidence,
                double timestampLatencyMS) {
            mLatencyMS[phase] = latencyMS;
            mConfidence[phase] = confidence;
            mTimestampLatencyMS[phase] = timestampLatencyMS;
        }

        void handleTestCompletion() {
            // Some USB devices have higher latency than the Google device.
            // We subtract that extra latency so that we are just using the Android device latency.
            mMeasuredLatencyMS = StatUtils.calculateMean(mLatencyMS);
            mLatencyOffsetMS = 0.0;
            if (mRouteId == TESTROUTE_USB) {
                UsbDeviceReport report = mUsbDeviceReport; // fetch volatile object
                if (report != null && report.isValid) {
                    mLatencyOffsetMS = report.latencyOffset;
                }
            }
            mMeanLatencyMS = mMeasuredLatencyMS - mLatencyOffsetMS;

            mMeanAbsoluteDeviation =
                    StatUtils.calculateMeanAbsoluteDeviation(
                            mMeanLatencyMS, mLatencyMS, mLatencyMS.length);
            mMeanConfidence = StatUtils.calculateMean(mConfidence);
            mMeanTimestampLatencyMS = StatUtils.calculateMean(mTimestampLatencyMS);
            if (mNativeAnalyzerThread != null) {
                // Get Stream Attributes
                mSampleRate = mNativeAnalyzerThread.getSampleRate();
                mHas24BitHardwareSupport = mNativeAnalyzerThread.has24BitHardwareSupport();
                mHardwareFormat = mNativeAnalyzerThread.getHardwareFormat();

                // direction-dependent
                int[] directions = new int[] {NativeAnalyzerThread.STREAM_INPUT,
                        NativeAnalyzerThread.STREAM_OUTPUT};
                for (int direction : directions) {
                    mIsLowLatencyStream[direction] =
                            mNativeAnalyzerThread.isLowLatencyStream(direction);

                    mBurstFrames[direction] =
                            mNativeAnalyzerThread.getBurstFrames(direction);

                    mCapacityFrames[direction] =
                            mNativeAnalyzerThread.getCapacityFrames(direction);

                    mIsMMapStream[direction] =
                            mNativeAnalyzerThread.isMMapStream(direction);
                }
            }

            mTestStatusText.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.GONE);

            mCaptureCode = mWavFileCapture.completeCapture();
        }

        boolean isMeasurementValid() {
            return mTestRun && mMeanLatencyMS > 1.0 && mMeanConfidence >= mRequiredConfidence;
        }

        boolean has24BitHardwareSupport() {
            return mHas24BitHardwareSupport;
        }

        String getResultString() {
            String result;

            if (!mRouteAvailable) {
                result = getString(R.string.audio_loopback_routenotavailable);
            } else if (!mTestRun) {
                result = getString(R.string.audio_loopback_testnotrun);
            } else if (mMeanConfidence < mRequiredConfidence) {
                result = String.format(getString(R.string.audio_loopback_insufficientconfidence),
                        mMeanConfidence, mRequiredConfidence);
            } else if (mMeanLatencyMS <= LOWEST_REASONABLE_LATENCY_MILLIS) {
                result = String.format(getString(R.string.audio_loopback_latencytoolow),
                        mMeanLatencyMS, LOWEST_REASONABLE_LATENCY_MILLIS);
            } else {
                // Print more info if we are correcting the latency measurement.
                String adjustment = "";
                if (mLatencyOffsetMS > 0.0) {
                    adjustment = String.format(Locale.US, "Measured Latency: %.2f ms\n"
                            + "Latency Offset: %.2f ms\n",
                        mMeasuredLatencyMS,
                        mLatencyOffsetMS);

                }
                result = String.format(Locale.US,
                        "Test Finished\nMean Latency: %.2f ms\n"
                            + "%s"
                            + "Mean Absolute Deviation: %.2f\n"
                            + "Confidence: %.2f\n"
                            + "Low Latency Path: [out:%s, in:%s]\n"
                            + "24 Bit Hardware Support: %s\n"
                            + "Timestamp Latency:%.2f ms",
                        mMeanLatencyMS,
                        adjustment,
                        mMeanAbsoluteDeviation,
                        mMeanConfidence,
                        mIsLowLatencyStream[NativeAnalyzerThread.STREAM_OUTPUT]
                                ? mYesString : mNoString,
                        mIsLowLatencyStream[NativeAnalyzerThread.STREAM_INPUT]
                                ? mYesString : mNoString,
                        mHas24BitHardwareSupport ? mYesString : mNoString,
                        mMeanTimestampLatencyMS);
            }

            return result;
        }

        // ReportLog Schema (per route)
        private static final String KEY_ROUTEINDEX = "route_index";
        private static final String KEY_LATENCY = "latency";
        private static final String KEY_CONFIDENCE = "confidence";
        private static final String KEY_MEANABSDEVIATION = "mean_absolute_deviation";
        private static final String KEY_IS_PERIPHERAL_ATTACHED = "is_peripheral_attached";
        private static final String KEY_INPUT_PERIPHERAL_NAME = "input_peripheral";
        private static final String KEY_OUTPUT_PERIPHERAL_NAME = "output_peripheral";
        private static final String KEY_TEST_PERIPHERAL_NAME = "test_peripheral_name";
        private static final String KEY_TIMESTAMP_LATENCY = "timestamp_latency";
        private static final String KEY_SAMPLE_RATE = "sample_rate";
        private static final String KEY_HAS_24_BIT_HARDWARE_SUPPORT =
                "has_24_bit_hardware_support";
        private static final String KEY_HARDWARE_FORMAT = "hardware_format";
        // key for output low-latency. Pre-existing key, don't change
        private static final String KEY_OUTPUT_IS_LOW_LATENCY = "is_low_latency";
        private static final String KEY_INPUT_IS_LOW_LATENCY = "input_is_low_latency";
        private static final String KEY_OUTPUT_BURST_FRAMES = "output_burst_frames";
        private static final String KEY_INPUT_BURST_FRAMES = "input_burst_frames";
        private static final String KEY_OUTPUT_CAPACITY_FRAMES = "output_capacity_frames";
        private static final String KEY_INPUT_CAPACITY_FRAMES = "input_capacity_frames";
        private static final String KEY_OUTPUT_IS_MMAP_STREAM = "output_is_mmap_stream";
        private static final String KEY_INPUT_IS_MMAP_STREAM = "input_is_mmap_stream";

        void recordTestResults(CtsVerifierReportLog reportLog) {
            reportLog.addValue(
                    KEY_ROUTEINDEX,
                    mRouteId,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_LATENCY,
                    mMeanLatencyMS,
                    ResultType.LOWER_BETTER,
                    ResultUnit.MS);

            reportLog.addValue(
                    KEY_CONFIDENCE,
                    mMeanConfidence,
                    ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_MEANABSDEVIATION,
                    mMeanAbsoluteDeviation,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_TEST_PERIPHERAL_NAME,
                    mDeviceName,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_TIMESTAMP_LATENCY,
                    mMeanTimestampLatencyMS,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_SAMPLE_RATE,
                    mSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_HAS_24_BIT_HARDWARE_SUPPORT,
                    mHas24BitHardwareSupport,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_HARDWARE_FORMAT,
                    mHardwareFormat,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUTPUT_IS_LOW_LATENCY,
                    mIsLowLatencyStream[NativeAnalyzerThread.STREAM_OUTPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_INPUT_IS_LOW_LATENCY,
                    mIsLowLatencyStream[NativeAnalyzerThread.STREAM_INPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUTPUT_BURST_FRAMES,
                    mBurstFrames[NativeAnalyzerThread.STREAM_OUTPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_INPUT_BURST_FRAMES,
                    mBurstFrames[NativeAnalyzerThread.STREAM_INPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUTPUT_CAPACITY_FRAMES,
                    mCapacityFrames[NativeAnalyzerThread.STREAM_OUTPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_INPUT_CAPACITY_FRAMES,
                    mCapacityFrames[NativeAnalyzerThread.STREAM_INPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUTPUT_IS_MMAP_STREAM,
                    mIsMMapStream[NativeAnalyzerThread.STREAM_OUTPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_INPUT_IS_MMAP_STREAM,
                    mIsMMapStream[NativeAnalyzerThread.STREAM_INPUT],
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }

        void addToJson(JSONObject jsonObject) {
            try {
                jsonObject.put(
                        KEY_ROUTEINDEX,
                        mRouteId);

                jsonObject.put(
                        KEY_LATENCY,
                        mMeanLatencyMS);

                jsonObject.put(
                        KEY_CONFIDENCE,
                        mMeanConfidence);

                jsonObject.put(
                        KEY_MEANABSDEVIATION,
                        mMeanAbsoluteDeviation);

                jsonObject.put(
                        KEY_TEST_PERIPHERAL_NAME,
                        mDeviceName);

                jsonObject.put(
                        KEY_TIMESTAMP_LATENCY,
                        mMeanTimestampLatencyMS);

                jsonObject.put(
                        KEY_SAMPLE_RATE,
                        mSampleRate);

                jsonObject.put(
                        KEY_HAS_24_BIT_HARDWARE_SUPPORT,
                        mHas24BitHardwareSupport);

                jsonObject.put(
                        KEY_HARDWARE_FORMAT,
                        mHardwareFormat);

                jsonObject.put(
                        KEY_OUTPUT_IS_LOW_LATENCY,
                        mIsLowLatencyStream[NativeAnalyzerThread.STREAM_OUTPUT]);

                jsonObject.put(
                        KEY_INPUT_IS_LOW_LATENCY,
                        mIsLowLatencyStream[NativeAnalyzerThread.STREAM_INPUT]);

                jsonObject.put(
                        KEY_OUTPUT_BURST_FRAMES,
                        mBurstFrames[NativeAnalyzerThread.STREAM_OUTPUT]);

                jsonObject.put(
                        KEY_INPUT_BURST_FRAMES,
                        mBurstFrames[NativeAnalyzerThread.STREAM_INPUT]);

                jsonObject.put(
                        KEY_OUTPUT_CAPACITY_FRAMES,
                        mCapacityFrames[NativeAnalyzerThread.STREAM_OUTPUT]);

                jsonObject.put(
                        KEY_INPUT_CAPACITY_FRAMES,
                        mCapacityFrames[NativeAnalyzerThread.STREAM_INPUT]);

                jsonObject.put(
                        KEY_OUTPUT_IS_MMAP_STREAM,
                        mIsMMapStream[NativeAnalyzerThread.STREAM_OUTPUT]);

                jsonObject.put(
                        KEY_INPUT_IS_MMAP_STREAM,
                        mIsMMapStream[NativeAnalyzerThread.STREAM_INPUT]);

            } catch (JSONException e) {
                Log.e(TAG, LOG_ERROR_STR, e);
            }
        }

        void recordPerformanceClassTestResults() {
            PerformanceClassEvaluator pce = new PerformanceClassEvaluator(mTestName);
            RoundTripAudioLatencyRequirement roundTripAudioLatencyRequirement =
                    Requirements.addR5_6__H_1_2().to(pce);
            TwentyFourBitAudioRequirement twentyFourBitAudioRequirement =
                    Requirements.addR5_6__H_1_3().to(pce);

            roundTripAudioLatencyRequirement.setRoundTripAudioLatencyMs(mMeanLatencyMS);
            twentyFourBitAudioRequirement.setTwentyFourBitAudioSupported(mHas24BitHardwareSupport);

            pce.submitAndVerify();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_loopback_latency_activity);

        super.onCreate(savedInstanceState);

        mContext = this;

        // MegaAudio Initialization
        StreamBase.setup(this);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_loopback_latency_test, R.string.audio_loopback_info, -1);

        mRequireReportLogToPass = true;

        mClaimsOutput = AudioSystemFlags.claimsOutput(this);
        mClaimsInput = AudioSystemFlags.claimsInput(this);
        mClaimsProAudio = AudioSystemFlags.claimsProAudio(this);
        mClaimsLowLatency = AudioSystemFlags.claimsLowLatencyAudio(this);
        mClaimsMediaPerformance = Build.VERSION.MEDIA_PERFORMANCE_CLASS != 0;
        mIsWatch = AudioSystemFlags.isWatch(this);
        mIsTV = AudioSystemFlags.isTV(this);
        mIsAutomobile = AudioSystemFlags.isAutomobile(this);
        mIsHandheld = AudioSystemFlags.isHandheld(this);
        mIsEmulator = Build.IS_EMULATOR;

        mUSBAudioSupport = AudioDeviceUtils.supportsUsbAudio(this);
        mAnalogJackSupport = AudioDeviceUtils.supportsAnalogHeadset(this);

        // Setup test specifications
        double mustLatency;

        // Speaker/Mic Path
        mTestSpecs[TESTROUTE_DEVICE] =
                new TestSpec(TESTROUTE_DEVICE, CONFIDENCE_THRESHOLD_AMBIENT);
        mTestSpecs[TESTROUTE_DEVICE].mRouteAvailable = true;    // Always

        // Analog Jack Path
        mTestSpecs[TESTROUTE_ANALOG_JACK] =
                new TestSpec(TESTROUTE_ANALOG_JACK, CONFIDENCE_THRESHOLD_WIRED);

        // USB Path
        mTestSpecs[TESTROUTE_USB] =
                new TestSpec(TESTROUTE_USB, CONFIDENCE_THRESHOLD_WIRED);

        // Setup UI
        mYesString = getString(R.string.audio_general_yes);
        mNoString = getString(R.string.audio_general_no);
        mPassString = getString(R.string.audio_general_teststatus_pass);
        mFailString = getString(R.string.audio_general_teststatus_fail);
        mNotTestedString = getString(R.string.audio_general_not_tested);
        mRequiredString = getString(R.string.audio_general_required);
        mNoHardwareString = getString(R.string.audio_general_nohardware);
        mUnknownHardwareString = getString(R.string.audio_general_unknownhardware);

        // Utilities
        mUtiltitiesHandler = new AudioLoopbackUtilitiesHandler(this);

        // Pro Audio
        ((TextView) findViewById(R.id.audio_loopback_pro_audio)).setText(
                (mClaimsProAudio ? mYesString : mNoString));

        // Low Latency
        ((TextView) findViewById(R.id.audio_loopback_low_latency)).setText(
                (mClaimsLowLatency ? mYesString : mNoString));

        // Media Performance Class
        ((TextView) findViewById(R.id.audio_loopback_mpc)).setText(
                (mClaimsMediaPerformance ? String.valueOf(Build.VERSION.MEDIA_PERFORMANCE_CLASS)
                        : mNoString));

        // MMAP
        ((TextView) findViewById(R.id.audio_loopback_mmap)).setText(
                (mSupportsMMAP ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_mmap_exclusive)).setText(
                (mSupportsMMAPExclusive ? mYesString : mNoString));

        // Device Type
        ((TextView) findViewById(R.id.audio_loopback_is_watch)).setText(
                (mIsWatch ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_is_TV)).setText(
                (mIsTV ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_is_automobile)).setText(
                (mIsAutomobile ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_is_handheld)).setText(
                (mIsHandheld ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_emulator)).setText(
                (mIsEmulator ? mYesString : mNoString));

        // Individual Test Results
        mRouteStatus[TESTROUTE_DEVICE] =
                (TextView) findViewById(R.id.audio_loopback_speakermicpath_info);
        mRouteStatus[TESTROUTE_ANALOG_JACK] =
                (TextView) findViewById(R.id.audio_loopback_headsetpath_info);
        mRouteStatus[TESTROUTE_USB] =
                (TextView) findViewById(R.id.audio_loopback_usbpath_info);

        mStartButtons[TESTROUTE_DEVICE] =
                (Button) findViewById(R.id.audio_loopback_speakermicpath_btn);
        mStartButtons[TESTROUTE_DEVICE].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_ANALOG_JACK] =
                (Button) findViewById(R.id.audio_loopback_headsetpath_btn);
        mStartButtons[TESTROUTE_ANALOG_JACK].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_USB] = (Button) findViewById(R.id.audio_loopback_usbpath_btn);
        mStartButtons[TESTROUTE_USB].setOnClickListener(mBtnClickListener);

        mTestInstructions = (TextView) findViewById(R.id.audio_loopback_instructions);

        mAudioManager = getSystemService(AudioManager.class);
        scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));

        // Utilties Buttons
        if (mIsWatch) {
            ((LinearLayout) findViewById(R.id.audio_loopback_utilities_layout))
                    .setOrientation(LinearLayout.VERTICAL);
        }

        mResultsWebView = findViewById(R.id.audio_loopback_results);

        connectLoopbackUI();

        if (mustRunTest()) {
            getPassButton().setEnabled(false);
        } else {
            getPassButton().setEnabled(isReportLogOkToPass());
        }

        mConnectListener = new ConnectListener();

        // WAV Capture
        String folderName = getDataDir().getAbsolutePath() + "/WavFiles";
        mFilesDir = new File(folderName);
        if (!mFilesDir.exists()) {
            mFilesDir.mkdir();
        }

        mWavFileCapture = new WavFileCapture();

        showRouteStatus();
        showTestInstructions();
        handleTestCompletion(false);

        DisplayUtils.setKeepScreenOn(this, true);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAudioManager.registerAudioDeviceCallback(mConnectListener, null);
    }

    @Override
    public void onStop() {
        mAudioManager.unregisterAudioDeviceCallback(mConnectListener);
        super.onStop();
    }

    //
    // UI State
    //
    private void showRouteStatus() {
        // mRouteStatus[TESTROUTE_DEVICE];
        // Nothing to do for this route.

        // mRouteStatus[TESTROUTE_ANALOG_JACK];
        switch (mAnalogJackSupport) {
            case AudioDeviceUtils.SUPPORTSDEVICE_NO:
                mRouteStatus[TESTROUTE_ANALOG_JACK].setText(
                        getString(R.string.audio_loopback_noanalog));
                break;
            case AudioDeviceUtils.SUPPORTSDEVICE_YES:
                mRouteStatus[TESTROUTE_ANALOG_JACK].setText(
                        getString(R.string.audio_loopback_headsetpath_instructions));
                break;
            case AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED:
                mRouteStatus[TESTROUTE_ANALOG_JACK].setText(
                        getString(R.string.audio_loopback_unknownanalog));
                break;
        }

        // mRouteStatus[TESTROUTE_USB];
        switch (mUSBAudioSupport) {
            case AudioDeviceUtils.SUPPORTSDEVICE_NO:
                mRouteStatus[TESTROUTE_USB].setText(getString(R.string.audio_loopback_nousb));
                break;
            case AudioDeviceUtils.SUPPORTSDEVICE_YES:
                mRouteStatus[TESTROUTE_USB].setText(
                        getString(R.string.audio_loopback_usbpath_instructions));
                break;
            case AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED:
                mRouteStatus[TESTROUTE_USB].setText(getString(R.string.audio_loopback_unknownusb));
                break;
        }
    }

    private void showTestInstructions() {
        if (mustRunTest()) {
            mTestInstructions.setText(getString(R.string.audio_loopback_test_all_paths));
        } else {
            mTestInstructions.setText(getString(R.string.audio_loopback_test_not_required));
        }
    }

    private void enableStartButtons(boolean enable) {
        if (enable) {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(mTestSpecs[routeId].mRouteConnected);
            }
        } else {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(false);
            }
        }
        mUtiltitiesHandler.setEnabled(enable);
    }

    private void connectLoopbackUI() {
        mTestStatusText = (TextView) findViewById(R.id.audio_loopback_status_text);
        mTestStatusText.setVisibility(View.GONE);
        mProgressBar = (ProgressBar) findViewById(R.id.audio_loopback_progress_bar);
        mProgressBar.setVisibility(View.GONE);
        showWait(false);
    }

    //
    // Peripheral Connection Logic
    //
    void clearDeviceIds() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mInputDeviceId = testSpec.mInputDeviceId = TestSpec.DEVICEID_NONE;
        }
    }

    void clearDeviceConnected() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mRouteConnected = false;
        }
    }

    void scanPeripheralList(AudioDeviceInfo[] devices) {
        clearDeviceIds();
        clearDeviceConnected();

        mSpeakerDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;
        mMicDeviceId = AudioDeviceInfo.TYPE_UNKNOWN;
        for (AudioDeviceInfo devInfo : devices) {
            switch (devInfo.getType()) {
                // TESTROUTE_DEVICE (i.e. Speaker & Mic)
                // This needs to be handled differently. The other devices can be assumed
                // to contain both input & output devices in the same type.
                // For built-in we need to see both TYPES to be sure to have both input & output.
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    mSpeakerDeviceId = devInfo.getId();
                    break;
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    mMicDeviceId = devInfo.getId();
                    break;

                // TESTROUTE_ANALOG_JACK
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_AUX_LINE:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mDeviceName =
                            devInfo.getProductName().toString();
                    break;

                // TESTROUTE_USB
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_USB].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_USB].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_USB].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_USB].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_USB].mDeviceName = devInfo.getProductName().toString();
                    break;
            }
        }

        // do we have BOTH a Speaker and Mic?
        if (hasInternalPath()) {
            mTestSpecs[TESTROUTE_DEVICE].mOutputDeviceId = mSpeakerDeviceId;
            mTestSpecs[TESTROUTE_DEVICE].mInputDeviceId = mMicDeviceId;
            mTestSpecs[TESTROUTE_DEVICE].mRouteAvailable = true;
            mTestSpecs[TESTROUTE_DEVICE].mRouteConnected = true;
            mTestSpecs[TESTROUTE_DEVICE].mDeviceName =
                    getString(R.string.audio_loopback_test_internal_devices);
        }

        enableStartButtons(mustRunTest());
    }

    private class ConnectListener extends AudioDeviceCallback {
        ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
            mUsbDeviceReport = AudioDeviceUtils.validateUsbDevice(mContext);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
            mUsbDeviceReport = null;
        }
    }

    //
    // show active progress bar
    //
    protected void showWait(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    //
    // Common logging
    //

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, "audio_loopback_latency_activity");
    }

    // Global Test-Schema
    private static final String KEY_IS_PRO_AUDIO = "is_pro_audio";
    private static final String KEY_TEST_MMAP = "supports_mmap";
    private static final String KEY_TEST_MMAPEXCLUSIVE = "supports_mmap_exclusive";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_PRODUCT_FIRST_API_LEVEL = "product_first_api_level";
    private static final String KEY_BOARD_FIRST_API_LEVEL = "board_first_api_level";
    private static final String KEY_OVERALL_PASS = "overall_pass";

    // Contains the results for all routes
    private static final String KEY_PATHS = "paths";

    private void recordGlobalResults(CtsVerifierReportLog reportLog) {
        // Leave this in to be sure not to break the ReportLog injestion
        reportLog.addValue(
                KEY_LEVEL,
                -1,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IS_PRO_AUDIO,
                mClaimsProAudio,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_TEST_MMAP,
                mSupportsMMAP,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_TEST_MMAPEXCLUSIVE,
                mSupportsMMAPExclusive,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                Common.KEY_VERSION_CODE,
                Common.VERSION_CODE,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_PRODUCT_FIRST_API_LEVEL,
                mFirstProductApiLevel,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_BOARD_FIRST_API_LEVEL,
                mFirstBoardApiLevel,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_OVERALL_PASS,
                mOverallPass,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    private void recordAllRoutes(CtsVerifierReportLog reportLog) {
        JSONArray jsonArray = new JSONArray();
        for (int route = 0; route < NUM_TEST_ROUTES; route++) {
            if (mTestSpecs[route].isMeasurementValid()) {
                JSONObject jsonObject = new JSONObject();
                mTestSpecs[route].addToJson(jsonObject);
                jsonArray.put(jsonObject);
            }
        }

        if (jsonArray.length() > 0) {
            reportLog.addValues(KEY_PATHS, jsonArray);
        }
    }

    @Override
    public void recordTestResults() {
        // Look for a valid route with the minimum latency.
        int bestRoute = -1;
        double minLatency = Double.MAX_VALUE;
        for (int route = 0; route < NUM_TEST_ROUTES; route++) {
            if (mTestSpecs[route].isMeasurementValid()) {
                if (mTestSpecs[route].mMeanLatencyMS < minLatency) {
                    bestRoute = route;
                    minLatency = mTestSpecs[route].mMeanLatencyMS;
                }
            }
        }

        if (bestRoute >= 0) {
            CtsVerifierReportLog reportLog = getReportLog();
            recordGlobalResults(reportLog);
            mTestSpecs[bestRoute].recordTestResults(reportLog);
            mTestSpecs[bestRoute].recordPerformanceClassTestResults();
            recordAllRoutes(reportLog);
            reportLog.submit();
        }
    }

    private void startAudioTest(Handler messageHandler, int testRouteId) {
        enableStartButtons(false);
        mRouteStatus[testRouteId].setText(getString(R.string.audio_loopback_running));

        mTestRoute = testRouteId;

        mTestSpecs[mTestRoute].startTest();

        getPassButton().setEnabled(false);

        mTestPhase = 0;

        // Set mmap enabled as mmap supported to get best latency
        Globals.setMMapEnabled(Globals.isMMapSupported());

        mNativeAnalyzerThread = new NativeAnalyzerThread(this);
        if (mNativeAnalyzerThread != null) {
            mNativeAnalyzerThread.setMessageHandler(messageHandler);
            // This value matches AAUDIO_INPUT_PRESET_VOICE_RECOGNITION
            mNativeAnalyzerThread.setInputPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            startTestPhase();
        } else {
            Log.e(TAG, "Couldn't allocate native analyzer thread");
            mTestStatusText.setText(getString(R.string.audio_loopback_failure));
        }
    }

    private void startTestPhase() {
        if (mNativeAnalyzerThread != null) {
            if (LOG) {
                Log.d(TAG, "mTestRoute: " + mTestRoute
                        + " mInputDeviceId: " + mTestSpecs[mTestRoute].mInputDeviceId
                        + " mOutputDeviceId: " + mTestSpecs[mTestRoute].mOutputDeviceId);
            }
            mNativeAnalyzerThread.startTest(
                    mTestSpecs[mTestRoute].mInputDeviceId, mTestSpecs[mTestRoute].mOutputDeviceId);

            // what is this for?
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTestPhaseCompletion() {
        if (mNativeAnalyzerThread != null && mTestPhase < NUM_TEST_PHASES) {
            double latency = mNativeAnalyzerThread.getLatencyMillis();
            double confidence = mNativeAnalyzerThread.getConfidence();
            double timestampLatency = mNativeAnalyzerThread.getTimestampLatencyMillis();
            TestSpec testSpec = mTestSpecs[mTestRoute];
            testSpec.recordPhase(mTestPhase, latency, confidence, timestampLatency);

            String result = String.format(getString(R.string.audio_loopback_testphaseresult),
                    mTestPhase, latency, confidence, timestampLatency);

            mTestStatusText.setText(result);
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
                // Thread.sleep(/*STOP_TEST_TIMEOUT_MSEC*/500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            mTestPhase++;
            if (mTestPhase >= NUM_TEST_PHASES) {
                handleTestCompletion(true);
            } else {
                startTestPhase();
            }
        }
    }

    private boolean mustRunTest() {
        // Special handling for emulators
        if (mIsEmulator) {
            return false;
        }

        return mIsHandheld && hasInternalPath();
    }

    boolean hasInternalPath() {
        return mSpeakerDeviceId != AudioDeviceInfo.TYPE_UNKNOWN
                && mMicDeviceId != AudioDeviceInfo.TYPE_UNKNOWN;
    }

    private boolean calcPass(LoopbackLatencyRequirements requirements) {
        if (!mustRunTest()) {
            // just grant a pass on non-handheld devices
            return true;
        }

        boolean pass = true;

        // Check to see if the tests supported by the hardware have run
        // Analog Headset
        if (mAnalogJackSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES
                && !mTestSpecs[TESTROUTE_ANALOG_JACK].mTestRun) {
            pass = false;
        }

        if (mUSBAudioSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES
                && !mTestSpecs[TESTROUTE_USB].mTestRun) {
            pass = false;
        }

        // Check if the test values have passed
        // Even if the test is already a fail, this will generate the results string
        pass &= requirements.evaluate(mClaimsProAudio,
                Build.VERSION.MEDIA_PERFORMANCE_CLASS,
                mTestSpecs[TESTROUTE_DEVICE].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_DEVICE].mMeanLatencyMS : 0.0,
                mTestSpecs[TESTROUTE_ANALOG_JACK].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_ANALOG_JACK].mMeanLatencyMS :  0.0,
                mTestSpecs[TESTROUTE_USB].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_USB].mMeanLatencyMS : 0.0,
                mTestSpecs[TESTROUTE_ANALOG_JACK].has24BitHardwareSupport(),
                mTestSpecs[TESTROUTE_USB].has24BitHardwareSupport(),
                mTestSpecs[TESTROUTE_DEVICE].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_DEVICE].mMeanTimestampLatencyMS : 0.0,
                mTestSpecs[TESTROUTE_ANALOG_JACK].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_ANALOG_JACK].mMeanTimestampLatencyMS :  0.0,
                mTestSpecs[TESTROUTE_USB].isMeasurementValid()
                        ? mTestSpecs[TESTROUTE_USB].mMeanTimestampLatencyMS : 0.0);

        return pass;
    }

    private void handleTestCompletion(boolean showResult) {
        TestSpec testSpec = mTestSpecs[mTestRoute];
        testSpec.handleTestCompletion();

        // Make sure the test thread is finished. It should already be done.
        if (mNativeAnalyzerThread != null) {
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mRouteStatus[mTestRoute].setText(testSpec.getResultString());

        LoopbackLatencyRequirements requirements = new LoopbackLatencyRequirements();
        mOverallPass = calcPass(requirements);

        getPassButton().setEnabled(mOverallPass);

        showWait(false);
        enableStartButtons(true);
    }

    /**
     * handler for messages from audio thread
     */
    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Locale locale = Locale.getDefault();

            switch(msg.what) {
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED:
                    Log.v(TAG,"got message native rec started!!");
                    showWait(true);
                    mTestStatusText.setText(String.format(locale,
                            getString(R.string.audio_loopback_phaserunning), (mTestPhase + 1)));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_OPEN_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText(getString(R.string.audio_loopback_erroropeningstream));
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText(getString(R.string.audio_loopback_errorwhilerecording));
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                    mTestStatusText.setText(getString(R.string.audio_loopback_failedduetoerrors));
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_ANALYZING:
                    mTestStatusText.setText(String.format(locale,
                            getString(R.string.audio_loopback_phaseanalyzing), mTestPhase + 1));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE:
                    handleTestPhaseCompletion();
                    break;
                default:
                    break;
            }
        }
    };

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audio_loopback_speakermicpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_DEVICE);
            } else if (id == R.id.audio_loopback_headsetpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_ANALOG_JACK);
            }  else if (id == R.id.audio_loopback_usbpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_USB);
            }
        }
    }

    class LoopbackLatencyRequirements {
        public static final int MPC_NONE = 0;
        public static final int MPC_R = Build.VERSION_CODES.R;
        public static final int MPC_S = Build.VERSION_CODES.S;
        public static final int MPC_T = Build.VERSION_CODES.TIRAMISU;

        String mResultsString = new String();

        String getResultsString() {
            return mResultsString;
        }

        private boolean checkLatency(double measured, double limit) {
            return measured == LATENCY_NOT_MEASURED || measured <= limit;
        }

        private boolean checkTimestampLatencyAccuracy(double measuredLatency,
                double timestampLatency) {
            return (timestampLatency < 0.0) || (measuredLatency == LATENCY_NOT_MEASURED)
                    || (Math.abs(measuredLatency - timestampLatency) <= TIMESTAMP_ACCURACY_MS);
        }

        /**
         * Calculate pass/not pass and generate on-screen messages.
         * @return true if the test is in a pass state.
         */
        public boolean evaluate(boolean proAudio,
                                       int mediaPerformanceClass,
                                       double deviceLatency,
                                       double analogLatency,
                                       double usbLatency,
                                       boolean analog24BitHardwareSupport,
                                       boolean usb24BitHardwareSupport,
                                       double deviceTimestampLatency,
                                       double analogTimestampLatency,
                                       double usbTimestampLatency) {

            if (LOG) {
                Log.d(TAG, "evaluate()");
            }

            /*
             * Calculate PASS/FAIL
             */

           // Required to test the Mic/Speaker path
            boolean internalPathRun = deviceLatency != LATENCY_NOT_MEASURED;
            if (LOG) {
                Log.d(TAG, "  internalPathRun:" + internalPathRun);
            }

            // *ALL* devices must be under the basic limit.
            boolean basicPass = checkLatency(deviceLatency, LATENCY_BASIC)
                    && checkLatency(analogLatency, LATENCY_BASIC)
                    && checkLatency(usbLatency, LATENCY_BASIC);
            if (LOG) {
                Log.d(TAG, "  basicPass:" + basicPass);
            }

            // For Media Performance Class T the RT latency must be <= 80 msec on one path.
            boolean mpcAtLeastOnePass;
            if (mClaimsMediaPerformance) {
                mpcAtLeastOnePass =
                    (mediaPerformanceClass < MPC_T)
                            || checkLatency(deviceLatency, LATENCY_MPC_AT_LEAST_ONE)
                            || checkLatency(analogLatency, LATENCY_MPC_AT_LEAST_ONE)
                            || checkLatency(usbLatency, LATENCY_MPC_AT_LEAST_ONE);
            } else {
                mpcAtLeastOnePass = true;
            }
            if (LOG) {
                Log.d(TAG, "  mpcAtLeastOnePass:" + mpcAtLeastOnePass);
            }

            // For ProAudio, the RT latency must be <= 25 msec on one path.
            boolean proAudioAtLeastOnePass = !proAudio
                    || checkLatency(deviceLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE)
                    || checkLatency(analogLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE)
                    || checkLatency(usbLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE);
            if (LOG) {
                Log.d(TAG, "  proAudioAtLeastOnePass:" + proAudioAtLeastOnePass);
            }

            // For ProAudio, analog and USB have specific limits
            boolean proAudioLimitsPass = !proAudio;
            if (proAudio) {
                if (analogLatency > 0.0) {
                    proAudioLimitsPass = analogLatency <= LATENCY_PRO_AUDIO_ANALOG;
                } else if (usbLatency > 0.0) {
                    // USB audio must be supported if 3.5mm jack not supported
                    proAudioLimitsPass = usbLatency <= LATENCY_PRO_AUDIO_USB;
                }
            }

            // For Media Performance Class T, usb and analog should support >=24 bit audio.
            boolean has24BitHardwareSupportPass = (mediaPerformanceClass < MPC_T)
                    || (analog24BitHardwareSupport && usb24BitHardwareSupport);
            if (LOG) {
                Log.d(TAG, "  has24BitHardwareSupportPass:" + has24BitHardwareSupportPass);
            }

            // Timestamp latencies must be accurate enough.
            boolean timestampPass =
                    checkTimestampLatencyAccuracy(deviceLatency, deviceTimestampLatency)
                    && checkTimestampLatencyAccuracy(analogLatency, analogTimestampLatency)
                    && checkTimestampLatencyAccuracy(usbLatency, usbTimestampLatency);
            if (LOG) {
                Log.d(TAG, "  timestampPass:" + timestampPass);
            }

            boolean pass =
                    internalPathRun
                    && basicPass
                    && mpcAtLeastOnePass
                    && proAudioAtLeastOnePass
                    && proAudioLimitsPass
                    && has24BitHardwareSupportPass;
            if (LOG) {
                Log.d(TAG, "  pass:" + pass);
            }

            /*
             * Display Results
             */
            buildResultsPanel(proAudio, mediaPerformanceClass);

            return pass;
        }

        private void reportCapture(TextFormatter textFormatter, int pathId) {
            if (mTestSpecs[pathId].mCaptureCode == WavFileCapture.CAPTURE_SUCCESS) {
                textFormatter.appendBreak()
                        .appendText(getString(R.string.audio_loopback_wavcapturefile) + " "
                                + mTestSpecs[pathId].mWavCaptureFileName);
            } else {
                if (mTestSpecs[pathId].mWavCaptureFileName == null) {
                    textFormatter.appendBreak()
                            .appendText(getString(R.string.audio_loopback_nocapture));
                } else {
                    textFormatter.appendBreak()
                            .openBold()
                            .appendText(getString(R.string.audio_loopback_nocapture)
                                + ": " + mTestSpecs[pathId].mWavCaptureFileName)
                            .closeBold();
                }
            }
        }

        private void buildResultsPanel(boolean proAudio, int mediaPerformanceClass) {
            // We will want to think about non-WebView devices
            TextFormatter textFormatter = new HtmlFormatter();
            textFormatter.openDocument();

            Locale locale = Locale.getDefault();

            /*
             * ReportLog Warning
             */
            if (!isReportLogOkToPass()) {
                textFormatter.openParagraph()
                        .appendText(getString(R.string.ctsv_general_cantwritereportlog))
                        .closeParagraph();
            }

            /*
             * Audio Performance Level Criteria
             */
            textFormatter.appendText(getString(R.string.ctsv_loopback_criteria))
                    .openBold();
            if (proAudio) {
                textFormatter.appendText(getString(R.string.audio_general_proaudio));
            } else if (mediaPerformanceClass != MPC_NONE) {
                textFormatter.appendText(getString(R.string.audio_general_mpc) + " "
                        + mediaPerformanceClass);
            } else {
                textFormatter.appendText(getString(R.string.audio_general_basicaudio));
            }
            textFormatter.closeBold()
                    .appendBreak();

            // Basic Pass
            textFormatter.appendText(getString(R.string.ctsv_loopback_forbasiclatency))
                    .appendBreak();
            textFormatter.appendText(getString(R.string.ctsv_loopback_atleastoneroute)
                            + LATENCY_BASIC + getString(R.string.ctsv_general_mssuffix))
                    .appendBreak();

            // Media Performance Class
            if (mClaimsMediaPerformance) {
                textFormatter.appendText(
                        getString(R.string.ctsv_loopback_formpclevel) + " " + mediaPerformanceClass)
                        .appendBreak()
                        .appendText(getString(R.string.ctsv_loopback_atleastoneroute)
                                + LATENCY_MPC_AT_LEAST_ONE
                                + getString(R.string.ctsv_general_mssuffix))
                        .appendBreak();
            }

            // Pro Audio
            if (proAudio) {
                textFormatter.appendText(getString(R.string.ctsv_loopback_forproaudio))
                        .appendBreak()
                        .appendText(getString(R.string.ctsv_loopback_analogheadsetspec)
                                + LATENCY_PRO_AUDIO_ANALOG
                                + getString(R.string.ctsv_general_mssuffix))
                        .appendBreak()
                        .appendText(getString(R.string.ctsv_loopback_usbspec)
                                + LATENCY_PRO_AUDIO_USB
                                + getString(R.string.ctsv_general_mssuffix))
                        .appendBreak();
            }

            textFormatter.appendBreak()
                    .appendText(getString(R.string.audio_loopback_wavcapturefolder))
                    .appendBreak()
                    .appendText(mFilesDir.getPath())
                    .appendBreak();

            /*
             * Speaker/Mic route
             */
            double speakermicLatency = mTestSpecs[TESTROUTE_DEVICE].mMeanLatencyMS;
            textFormatter.openParagraph()
                    .openBold()
                    .appendText(getString(R.string.audio_loopback_speakermic))
                    .closeBold()
                    .appendBreak();
            if (speakermicLatency == LATENCY_NOT_MEASURED) {
                textFormatter.openItalic()
                        .appendText(getString(R.string.ctsv_loopback_testspeakermic))
                        .closeItalic();
            } else {
                textFormatter.appendText(String.format(locale, "%.2f ms ", speakermicLatency));
                if (speakermicLatency <= LATENCY_PRO_AUDIO_AT_LEAST_ONE) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsproaudio)
                            + String.format(locale, " (%.2f ms)", LATENCY_PRO_AUDIO_AT_LEAST_ONE));
                } else if (mClaimsMediaPerformance
                        && speakermicLatency <= LATENCY_MPC_AT_LEAST_ONE) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsmpclevel) + " "
                            + Build.VERSION.MEDIA_PERFORMANCE_CLASS + " "
                            + getString(R.string.ctsv_general_specification)
                            + String.format(locale, " (%.2f ms)", LATENCY_MPC_AT_LEAST_ONE));
                } else if (speakermicLatency <= LATENCY_BASIC) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsbasicaudio)
                            + String.format(locale, " (%.2f ms)", LATENCY_BASIC));
                } else {
                    textFormatter.appendText(getString(R.string.ctsv_general_failsuffix));
                }
            }

            // Capture Info
            reportCapture(textFormatter, TESTROUTE_DEVICE);
            textFormatter.closeParagraph();

            /*
             * Analog Headset Route
             */
            double analogLatency = mTestSpecs[TESTROUTE_ANALOG_JACK].mMeanLatencyMS;
            textFormatter.openParagraph()
                    .openBold()
                    .appendText(getString(R.string.audio_loopback_headset))
                    .closeBold()
                    .appendBreak();
            if (analogLatency != LATENCY_NOT_MEASURED) {
                // we have a legit measurement
                textFormatter.appendText(String.format(locale, "%.2f ms ", analogLatency));
                if (analogLatency <= LATENCY_PRO_AUDIO_ANALOG) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsproaudio)
                            + String.format(locale, " (%.2f ms)", LATENCY_PRO_AUDIO_ANALOG));
                } else if (mClaimsMediaPerformance && analogLatency <= LATENCY_MPC_AT_LEAST_ONE) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsmpclevel) + " "
                            + Build.VERSION.MEDIA_PERFORMANCE_CLASS + " "
                            + getString(R.string.ctsv_general_specification)
                            + String.format(locale, " (%.2f ms)", LATENCY_MPC_AT_LEAST_ONE));
                } else if (analogLatency <= LATENCY_BASIC) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsbasicaudio)
                            + String.format(locale, " (%.2f ms)", LATENCY_BASIC));
                } else {
                    textFormatter.appendText(getString(R.string.ctsv_general_failsuffix));
                }
            } else {
                // Not measured
                textFormatter.openItalic();
                switch (mAnalogJackSupport) {
                    case AudioDeviceUtils.SUPPORTSDEVICE_YES:
                        textFormatter.appendText(getString(R.string.ctsv_loopback_testanalogjack));
                        break;

                    case AudioDeviceUtils.SUPPORTSDEVICE_NO:
                        textFormatter.appendText(
                                getString(R.string.ctsv_loopback_noanalogjacksupport));
                        break;

                    case AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED:
                    default:
                        textFormatter.appendText(
                                getString(R.string.ctsv_loopback_unknownanalogsupport));
                        break;
                }
                textFormatter.closeItalic();
            }

            // Capture Info
            reportCapture(textFormatter, TESTROUTE_ANALOG_JACK);
            textFormatter.closeParagraph();

            /*
             * USB Device
             */
            double usbLatency = mTestSpecs[TESTROUTE_USB].mMeanLatencyMS;
            textFormatter.openParagraph()
                    .openBold()
                    .appendText(getString(R.string.audio_loopback_usb))
                    .closeBold()
                    .appendBreak();
            if (usbLatency != LATENCY_NOT_MEASURED) {
                textFormatter.appendText(String.format(locale, "%.2f ms ", usbLatency));
                if (usbLatency <= LATENCY_PRO_AUDIO_USB) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsproaudio)
                            + String.format(locale, " (%.2f ms)", LATENCY_PRO_AUDIO_USB));
                } else if (mClaimsMediaPerformance && usbLatency <= LATENCY_MPC_AT_LEAST_ONE) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsmpclevel) + " "
                            + Build.VERSION.MEDIA_PERFORMANCE_CLASS + " "
                            + getString(R.string.ctsv_general_specification)
                            + String.format(locale, " (%.2f ms", LATENCY_MPC_AT_LEAST_ONE));
                } else if (usbLatency <= LATENCY_BASIC) {
                    textFormatter.appendText(" - "
                            + getString(R.string.ctsv_loopback_meetsbasicaudio)
                            + String.format(locale, " (%.2f ms)", LATENCY_BASIC));
                } else {
                    textFormatter.appendText(getString(R.string.ctsv_general_failsuffix));
                }
            } else {
                // Not measured
                textFormatter.openItalic();
                switch (mUSBAudioSupport) {
                    case AudioDeviceUtils.SUPPORTSDEVICE_YES:
                        textFormatter.appendText(getString(R.string.ctsv_loopback_testusb));
                        break;

                    case AudioDeviceUtils.SUPPORTSDEVICE_NO:
                        textFormatter.appendText(getString(R.string.ctsv_loopback_nousbsupport));
                        break;

                    case AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED:
                    default:
                        textFormatter.appendText(
                                getString(R.string.ctsv_loopback_unknownusbsupport));
                        break;
                }
                textFormatter.closeItalic();
            }

            // Capture Info
            reportCapture(textFormatter, TESTROUTE_USB);
            textFormatter.closeParagraph();

            textFormatter.openParagraph();

            boolean analogRequired = mAnalogJackSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES;
            boolean usbRequired = mUSBAudioSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES;

            // First, do we have enough data to present a result.
            // Have all available paths been tested.
            boolean testSufficient =
                    speakermicLatency != LATENCY_NOT_MEASURED
                    && (analogLatency != LATENCY_NOT_MEASURED || !analogRequired)
                    && (usbLatency != LATENCY_NOT_MEASURED || !usbRequired);

            if (!testSufficient) {
                textFormatter.openItalic()
                        .appendText(getString(R.string.ctsv_loopback_testallroutes))
                        .closeItalic();
            } else {
                // All devices must be under the basic limit.
                boolean basicPass = checkLatency(speakermicLatency, LATENCY_BASIC)
                        && checkLatency(analogLatency, LATENCY_BASIC)
                        && checkLatency(usbLatency, LATENCY_BASIC);

                textFormatter.appendText(getString(R.string.audio_loopback_basiclatency) + " ")
                        .openBold()
                        .appendText(getString(basicPass
                                ? R.string.ctsv_general_pass : R.string.ctsv_general_fail))
                        .closeBold()
                        .appendBreak();

                // MPC
                boolean mpcAtLeastOnePass =
                        (mediaPerformanceClass < MPC_T)
                        || checkLatency(speakermicLatency, LATENCY_MPC_AT_LEAST_ONE)
                        || (analogRequired && checkLatency(analogLatency, LATENCY_MPC_AT_LEAST_ONE))
                        || (usbRequired && checkLatency(usbLatency, LATENCY_MPC_AT_LEAST_ONE));

                textFormatter.appendText(getString(R.string.audio_loopback_mpclatency) + " "
                                + mediaPerformanceClass + ": ")
                        .openBold();
                if (mClaimsMediaPerformance) {
                    textFormatter.appendText(getString(mpcAtLeastOnePass
                                    ? R.string.ctsv_general_pass : R.string.ctsv_general_fail));
                } else {
                    textFormatter.appendText(getString(R.string.audio_loopback_nopmpcsupport));
                }
                textFormatter.closeBold()
                        .appendBreak();

                // Pro Audio
                // For ProAudio, the RT latency must be <= 25 msec on one path.
                boolean proAudioAtLeastOnePass =
                        checkLatency(speakermicLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE)
                        || (analogRequired
                                && checkLatency(analogLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE))
                        || (usbRequired
                                && checkLatency(usbLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE));

                textFormatter
                        .appendText(getString(R.string.audio_loopback_proaudiolatency) + ": ")
                        .openBold();
                if (mClaimsProAudio) {
                    textFormatter.appendText(
                            getString(
                                    proAudioAtLeastOnePass
                                            ? R.string.ctsv_general_pass
                                            : R.string.ctsv_general_fail));
                } else {
                    textFormatter.appendText(getString(R.string.audio_loopback_noproaudiosupport));
                }
                textFormatter.closeBold().appendBreak();
            }

            textFormatter.closeParagraph()
                    .closeDocument();

            textFormatter.put(mResultsWebView);
            mResultsWebView.setVisibility(View.VISIBLE);
        }
    }
}
