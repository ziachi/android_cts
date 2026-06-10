/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Intent;
import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.analyzers.BaseSineAnalyzer;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.WaveFileWriter;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;
import com.android.cts.verifier.libs.ui.HtmlFormatter;
import com.android.cts.verifier.libs.ui.PlainTextFormatter;
import com.android.cts.verifier.libs.ui.TextFormatter;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.Globals;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CtsVerifier test for audio data paths.
 */
public abstract class AudioDataPathsBaseActivity
        extends AudioMultiApiActivity
        implements View.OnClickListener, AppCallback {
    private static final String TAG = "AudioDataPathsActivity";

    // ReportLog Schema
    private static final String SECTION_AUDIO_DATAPATHS = "audio_datapaths";

    protected boolean mHasMic;
    protected boolean mHasSpeaker;

    // UI
    protected View mStartButton;
    protected View mCancelButton;
    protected View mClearResultsButton;
    protected View mShowResultsButton;
    protected View mShareResultsButton;

    protected AudioLoopbackUtilitiesHandler mUtiltitiesHandler;

    private TextView mRoutesTx;
    private View mResultsView;

    private WaveScopeView mWaveView = null;

    private TextFormatter mTextFormatter;

    // Test Manager
    protected TestManager mTestManager = new TestManager();
    private boolean mTestHasBeenRun;
    private boolean mTestCanceledByUser;

    // Audio I/O
    private AudioManager mAudioManager;

    AudioDeviceConnectionCallback mConnectListener;

    private boolean mSupportsMMAP;
    private boolean mSupportsMMAPExclusive;

    protected boolean mIsHandheld;

    // Analysis
    private BaseSineAnalyzer mAnalyzer = new BaseSineAnalyzer();

    private DuplexAudioManager mDuplexAudioManager;

    protected AppCallback mAnalysisCallbackHandler;
    private File mRecordingDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MegaAudio Initialization
        StreamBase.setup(this);

        //
        // Header Fields
        //
        // When it is first created, isMMapEnabled will always return false due to b/326989822
        // Reenable this code when the fix lands in extern/oboe.
        mSupportsMMAP = Globals.isMMapSupported() /*&& Globals.isMMapEnabled()*/;
        mSupportsMMAPExclusive = Globals.isMMapExclusiveSupported() /*&& Globals.isMMapEnabled()*/;

        mHasMic = AudioSystemFlags.claimsInput(this);
        mHasSpeaker = AudioSystemFlags.claimsOutput(this);

        mIsHandheld = AudioSystemFlags.isHandheld(this);

        String yesString = getResources().getString(R.string.audio_general_yes);
        String noString = getResources().getString(R.string.audio_general_no);
        ((TextView) findViewById(R.id.audio_datapaths_mic))
                .setText(mHasMic ? yesString : noString);
        ((TextView) findViewById(R.id.audio_datapaths_speaker))
                .setText(mHasSpeaker ? yesString : noString);
        ((TextView) findViewById(R.id.audio_datapaths_MMAP))
                .setText(mSupportsMMAP ? yesString : noString);
        ((TextView) findViewById(R.id.audio_datapaths_MMAP_exclusive))
                .setText(mSupportsMMAPExclusive ? yesString : noString);

        // Utilities
        mUtiltitiesHandler = new AudioLoopbackUtilitiesHandler(this);

        mStartButton = findViewById(R.id.audio_datapaths_start);
        mStartButton.setOnClickListener(this);
        mCancelButton = findViewById(R.id.audio_datapaths_cancel);
        mCancelButton.setOnClickListener(this);
        mCancelButton.setEnabled(false);
        (mClearResultsButton =
                findViewById(R.id.audio_datapaths_clearresults)).setOnClickListener(this);
        (mShowResultsButton =
                findViewById(R.id.audio_datapaths_showresults)).setOnClickListener(this);
        (mShareResultsButton =
                findViewById(R.id.audio_datapaths_shareresults)).setOnClickListener(this);
        mRoutesTx = (TextView) findViewById(R.id.audio_datapaths_routes);

        LinearLayout resultsLayout = findViewById(R.id.audio_datapaths_results);
        if (AudioSystemFlags.supportsWebView(this)) {
            mTextFormatter = new HtmlFormatter();
            mResultsView = new WebView(this);
        } else {
            // No WebView
            mTextFormatter = new PlainTextFormatter();
            mResultsView = new TextView(this);
        }
        resultsLayout.addView(mResultsView,
                new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        boolean isWatch = AudioSystemFlags.isWatch(this);
        if (isWatch) {
            // Device Attributes Header
            ((LinearLayout) findViewById(R.id.audio_datapaths_header))
                    .setOrientation(LinearLayout.VERTICAL);
            // Utilities UI
            ((LinearLayout) findViewById(R.id.audio_loopback_utilities_layout))
                    .setOrientation(LinearLayout.VERTICAL);
        }

        mWaveView = findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);

        setPassFailButtonClickListeners();

        mAudioManager = getSystemService(AudioManager.class);

        mAnalysisCallbackHandler = this;

        mTestManager.initializeTests();

        mConnectListener = new AudioDeviceConnectionCallback();

        DisplayUtils.setKeepScreenOn(this, true);

        getPassButton().setEnabled(!mIsHandheld || !hasPeripheralSupport());
        if (!mIsHandheld) {
            displayNonHandheldMessage();
        }

        // Write to a directory that can be read on production builds using 'adb pull'.
        // This works because we have WRITE_EXTERNAL_STORAGE permission.
        mRecordingDir = new File(Environment.getExternalStorageDirectory(), "verifierWaves");
        if (!mRecordingDir.exists()) {
            mRecordingDir.mkdir();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mAudioManager.registerAudioDeviceCallback(mConnectListener, null);
    }

    @Override
    public void onStop() {
        stopTest();
        mAudioManager.unregisterAudioDeviceCallback(mConnectListener);
        super.onStop();
    }

    //
    // UI Helpers
    //
    protected abstract String getTestCategory();

    protected abstract boolean grantAutoPass();

    protected void enableTestButtons(boolean enabled) {
        mStartButton.setEnabled(enabled);
        mClearResultsButton.setEnabled(enabled);
        mShareResultsButton.setEnabled(enabled);
    }

    void enableTestButtons(boolean startEnabled, boolean stopEnabled) {
        mStartButton.setEnabled(startEnabled);
        mClearResultsButton.setEnabled(startEnabled);
        mShareResultsButton.setEnabled(startEnabled);
        mCancelButton.setEnabled(stopEnabled);
    }

    private void showDeviceView() {
        mRoutesTx.setVisibility(View.VISIBLE);
        mWaveView.setVisibility(View.VISIBLE);

        mResultsView.setVisibility(View.GONE);
    }

    private void showResultsView() {
        mRoutesTx.setVisibility(View.GONE);
        mWaveView.setVisibility(View.GONE);

        mResultsView.setVisibility(View.VISIBLE);
        mResultsView.invalidate();
    }

    class TestModule implements Cloneable {
        //
        // Analysis Type
        //
        public static final int TYPE_SIGNAL_PRESENCE    = 0;
        public static final int TYPE_SIGNAL_ABSENCE     = 1;
        private int mAnalysisType = TYPE_SIGNAL_PRESENCE;

        //
        // Datapath specifications
        //
        // Playback Specification
        final int mOutDeviceType; // TYPE_BUILTIN_SPEAKER for example
        final int mOutSampleRate;
        final int mOutChannelCount;
        int mOutPerformanceMode;
        //TODO - Add usage and content types to output stream

        // Device for capturing the (played) signal
        final int mInDeviceType;  // TYPE_BUILTIN_MIC for example
        final int mInSampleRate;
        final int mInChannelCount;
        int mInPerformanceMode;

        int mAnalysisChannel = 0;
        int mInputPreset;
        int mModuleIndex;

        AudioDeviceInfo mOutDeviceInfo;
        AudioDeviceInfo mInDeviceInfo;

        static final int TRANSFER_LEGACY = 0;
        static final int TRANSFER_MMAP_SHARED = 1;
        static final int TRANSFER_MMAP_EXCLUSIVE = 2;
        int mTransferType = TRANSFER_LEGACY;

        public AudioSourceProvider mSourceProvider;
        public AudioSinkProvider mSinkProvider;

        private String mSectionTitle = null;
        private String mDescription = "";

        private String mSavedFileMessage; // OK if null

        private static final String PLAYER_FAILED_TO_GET_STRING = "Player failed to get ";
        private static final String RECORDER_FAILED_TO_GET_STRING = "Recorder failed to get ";

        int[] mTestStateCode;
        TestStateData[] mTestStateData;

        TestResults[] mTestResults;

        // Pass/Fail criteria (with defaults)
        static final double MIN_SIGNAL_PASS_MAGNITUDE = 0.005;
        static final double MAX_SIGNAL_PASS_JITTER = 0.1;
        static final double MAX_XTALK_PASS_MAGNITUDE = 0.02;

        //
        // A set of classes to store information specific to the
        // different failure modes
        //
        static class TestStateData {
            final TestModule  mTestModule;

            final boolean mPlayerFailed;
            final boolean mRecorderFailed;

            TestStateData(TestModule testModule,
                          boolean playerFailed, boolean recorderFailed) {
                mTestModule = testModule;

                mPlayerFailed = playerFailed;
                mRecorderFailed = recorderFailed;
            }

            // Recorder and/or Player
            String buildComponentString() {
                StringBuilder sb = new StringBuilder();
                if (mPlayerFailed) {
                    sb.append("Player");
                    if (mRecorderFailed) {
                        sb.append("|");
                    }
                }
                if (mRecorderFailed) {
                    sb.append("Recorder");
                }
                return sb.toString();
            }

            String buildErrorString(TestModule testModule) {
                return "Error: - " + buildComponentString();
            }
        }

        //
        // Stores information about sharing mode failures
        //
        class BadSharingTestState extends TestStateData {

            BadSharingTestState(TestModule testModule,
                                boolean playerFailed, boolean recorderFailed) {
                super(testModule, playerFailed, recorderFailed);
            }

            String buildErrorString(TestModule testModule) {
                StringBuilder sb = new StringBuilder();

                if (testModule.mTransferType == TRANSFER_LEGACY) {
                    sb.append(" SKIP: can't set LEGACY mode");
                } else if (testModule.mTransferType == TRANSFER_MMAP_EXCLUSIVE) {
                    sb.append(" SKIP: can't set MMAP EXCLUSIVE mode");
                } else {
                    sb.append(" SKIP: can't set MMAP SHARED mode");
                }

                sb.append(" - " + buildComponentString());

                return sb.toString();
            }
        }

        class BadMMAPTestState extends TestStateData {
            BadMMAPTestState(TestModule testModule,
                                boolean playerFailed, boolean recorderFailed) {
                super(testModule, playerFailed, recorderFailed);
            }

            String buildErrorString(TestModule testModule) {
                StringBuilder sb = new StringBuilder();
                sb.append(" Didn't get MMAP");
                sb.append(" - " + buildComponentString());

                return sb.toString();
            }
        }

        TestModule(int outDeviceType, int outSampleRate, int outChannelCount,
                   int inDeviceType, int inSampleRate, int inChannelCount) {
            mOutDeviceType = outDeviceType;
            mOutSampleRate = outSampleRate;
            mOutChannelCount = outChannelCount;

            // Default
            mInDeviceType = inDeviceType;
            mInChannelCount = inChannelCount;
            mInSampleRate = inSampleRate;

            initializeTestState();
        }

        private void initializeTestState() {
            mTestStateCode = new int[NUM_TEST_APIS];
            mTestStateData = new TestStateData[NUM_TEST_APIS];
            for (int api = 0; api < NUM_TEST_APIS; api++) {
                mTestStateCode[api] = TestModule.TESTSTATUS_NOT_RUN;
            }
            mTestResults = new TestResults[NUM_TEST_APIS];
        }

        /**
         * We need a more-or-less deep copy so as not to share mTestState and mTestResults
         * arrays. We are only using this to setup closely related test modules, so it is
         * sufficient to initialize mTestState and mTestResults to their "not tested" states.
         *
         * @return The (mostly) cloned TestModule object.
         */
        @Override
        public TestModule clone() throws CloneNotSupportedException {
            // this will clone all the simple data members
            TestModule clonedModule = (TestModule) super.clone();

            // Each clone needs it own set of states and results
            clonedModule.initializeTestState();

            return clonedModule;
        }

        public int getModuleIndex() {
            return mModuleIndex;
        }

        public void setModuleIndex(int index) {
            this.mModuleIndex = index;
        }

        public void setSavedFileMessage(String s) {
            mSavedFileMessage = s;
        }

        /**
         * A message generated when saving a WAV file.
         *
         * @return message or null
         */
        public String getSavedFileMessage() {
            return mSavedFileMessage;
        }

        public void setAnalysisType(int type) {
            mAnalysisType = type;
        }

        // Test states that indicate a not run or successful (not failures) test are
        // zero or positive
        // Test states that indicate an executed test that failed are negative.
        public static final int TESTSTATUS_NOT_RUN = 1;
        public static final int TESTSTATUS_RUN = 0;
        public static final int TESTSTATUS_BAD_START = -1;
        public static final int TESTSTATUS_BAD_ROUTING = -2;
        public static final int TESTSTATUS_BAD_ANALYSIS_CHANNEL = -3;
        public static final int TESTSTATUS_CANT_SET_MMAP = -4;
        public static final int TESTSTATUS_BAD_SHARINGMODE = -5;
        public static final int TESTSTATUS_MISMATCH_MMAP = -6;  // we didn't get the MMAP mode
                                                                // we asked for
        public static final int TESTSTATUS_BAD_BUILD = -7;

        void clearTestState(int api) {
            mTestStateCode[api] = TESTSTATUS_NOT_RUN;
            mTestResults[api] = null;
            mTestHasBeenRun = false;
            mTestCanceledByUser = false;
        }

        int getTestState(int api) {
            return mTestStateCode[api];
        }

        int setTestState(int api, int state, TestStateData data) {
            mTestStateData[api] = data;
            return mTestStateCode[api] = state;
        }

        String getOutDeviceName() {
            return AudioDeviceUtils.getShortDeviceTypeName(mOutDeviceType);
        }

        String getInDeviceName() {
            return AudioDeviceUtils.getShortDeviceTypeName(mInDeviceType);
        }

        void setSectionTitle(String title) {
            mSectionTitle = title;
        }

        String getSectionTitle() {
            return mSectionTitle;
        }

        void setDescription(String description) {
            mDescription = description;
        }

        String getDescription() {
            return "(" + getModuleIndex() + ") " + mDescription
                    + "-" + transferTypeToString(mTransferType)
                    + ":" + performanceModeToString(mOutPerformanceMode);
        }

        void setAnalysisChannel(int channel) {
            mAnalysisChannel = channel;
        }

        void setSources(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
            mSourceProvider = sourceProvider;
            mSinkProvider = sinkProvider;
        }

        void setInputPreset(int preset) {
            mInputPreset = preset;
        }

        void setTransferType(int type) {
            mTransferType = type;
        }

        boolean canRun() {
            return mInDeviceInfo != null && mOutDeviceInfo != null;
        }

        void setTestResults(int api, BaseSineAnalyzer analyzer) {
            mTestResults[api] = new TestResults(api,
                    analyzer.getMagnitude(),
                    analyzer.getMaxMagnitude(),
                    analyzer.getPhaseOffset(),
                    analyzer.getPhaseJitter());
        }

        //
        // Predicates
        //
        // Ran to completion, maybe with failures
        boolean hasRun(int api) {
            return mTestStateCode[api] != TESTSTATUS_NOT_RUN;
        }

        // Ran and passed the criteria
        boolean hasPassed(int api) {
            boolean passed = false;
            if (hasRun(api) && mTestResults[api] != null) {
                if (mAnalysisType == TYPE_SIGNAL_PRESENCE) {
                    passed =
                            mTestResults[api].mMaxMagnitude >= MIN_SIGNAL_PASS_MAGNITUDE
                                    && mTestResults[api].mPhaseJitter <= MAX_SIGNAL_PASS_JITTER;
                } else {
                    passed = mTestResults[api].mMaxMagnitude <= MAX_XTALK_PASS_MAGNITUDE;
                }
            }
            return passed;
        }

        // Should've been able to run, but ran into errors opening/starting streams
        boolean hasError(int api) {
            // TESTSTATUS_NOT_RUN && TESTSTATUS_RUN are not errors
            return mTestStateCode[api] < 0;
        }

        //
        // UI Helpers
        //
        static String transferTypeToString(int transferType) {
            switch (transferType) {
                case TRANSFER_LEGACY:
                    return "Legacy";
                case TRANSFER_MMAP_SHARED:
                    return "MMAP-Shared";
                case TRANSFER_MMAP_EXCLUSIVE:
                    return "MMAP-Exclusive";
                default:
                    return "Unknown Transfer Type [" + transferType + "]";
            }
        }

        static String transferTypeToSharingString(int transferType) {
            switch (transferType) {
                case TRANSFER_LEGACY:
                case TRANSFER_MMAP_SHARED:
                    return "Shared";
                case TRANSFER_MMAP_EXCLUSIVE:
                    return "Exclusive";
                default:
                    return "Unknown Transfer Type [" + transferType + "]";
            }
        }

        String performanceModeToString(int performanceMode) {
            switch (performanceMode) {
                case BuilderBase.PERFORMANCE_MODE_NONE:
                    return getString(R.string.perf_mode_none_abreviation);
                case BuilderBase.PERFORMANCE_MODE_POWERSAVING:
                    return getString(R.string.perf_mode_powersave_abreviation);
                case BuilderBase.PERFORMANCE_MODE_LOWLATENCY:
                    return getString(R.string.perf_mode_lowlatency_abreviation);
                default:
                    return getString(R.string.perf_mode_none_abreviation);
            }
        }

        // {device}:{channel}:{channelCount}:{SR}:{path}
        // SpeakerSafe:0:2:48000:Legacy
        String formatOutputAttributes() {
            String deviceName = AudioDeviceUtils.getShortDeviceTypeName(mOutDeviceType);
            return deviceName + ":" + mAnalysisChannel
                    + ":" + mOutChannelCount
                    + ":" + mOutSampleRate
                    + ":" + transferTypeToString(mTransferType)
                    + ":" + performanceModeToString(mOutPerformanceMode);
        }

        String formatInputAttributes() {
            String deviceName = AudioDeviceUtils.getShortDeviceTypeName(mInDeviceType);
            return deviceName + ":" + mAnalysisChannel
                    + ":" + mInChannelCount
                    + ":" + mInSampleRate
                    + ":" + transferTypeToString(mTransferType);
        }

        String getTestStateString(int api) {
            int state = getTestState(api);
            switch (state) {
                case TESTSTATUS_NOT_RUN:
                    return " NOT TESTED";
                case TESTSTATUS_RUN:
                    if (mTestResults[api] == null) {
                        // This can happen when the test sequence is cancelled.
                        return " NO RESULTS";
                    } else {
                        return hasPassed(api) ? " PASS" : " FAIL";
                    }
                case TESTSTATUS_BAD_START:
                    return " BAD START - Couldn't start streams";
                case TESTSTATUS_BAD_BUILD:
                    return " BAD BUILD - Couldn't open streams";
                case TESTSTATUS_BAD_ROUTING:
                    return " BAD ROUTE";
                case TESTSTATUS_BAD_ANALYSIS_CHANNEL:
                    return " BAD ANALYSIS CHANNEL";
                case TESTSTATUS_CANT_SET_MMAP:
                case TESTSTATUS_MISMATCH_MMAP: {
                    BadMMAPTestState errorData = (BadMMAPTestState) mTestStateData[api];
                    return errorData.buildErrorString(this);
                }
                case TESTSTATUS_BAD_SHARINGMODE: {
                    BadSharingTestState errorData = (BadSharingTestState) mTestStateData[api];
                    return errorData.buildErrorString(this);
                }
                default:
                    return " UNKNOWN STATE ID [" + state + "]";
            }
        }

        private void logBeginning(int api) {
            Log.d(TAG, "BEGIN_SUB_TEST: " + getDescription() + ", " + audioApiToString(api));
        }

        private void logEnding(int api) {
            Log.d(TAG, "END_SUB_TEST: " + getDescription()
                    + ", " + audioApiToString(api)
                    + "," + getTestStateString(api)); // has leading space!
        }

        //
        // Process
        //

        /**
         * Note: The caller unwinds the DuplexManager if the start fails.
         * @param api Specifies the API (Java or Native) to use.
         * @return a TESTSTATUS_ constant indicating the result.
         */
        private int startTestModule(int api) {
            logBeginning(api);
            if (mOutDeviceInfo != null && mInDeviceInfo != null) {
                mAnalyzer.reset();
                mAnalyzer.setSampleRate(mInSampleRate);
                if (mAnalysisChannel < mInChannelCount) {
                    mAnalyzer.setInputChannel(mAnalysisChannel);
                } else {
                    Log.e(TAG, "Invalid analysis channel " + mAnalysisChannel
                            + " for " + mInChannelCount + " input signal.");
                    return setTestState(api, TESTSTATUS_BAD_ANALYSIS_CHANNEL, null);
                }

                // Player
                mDuplexAudioManager.setSources(mSourceProvider, mSinkProvider);
                mDuplexAudioManager.setPlayerRouteDevice(mOutDeviceInfo);
                mDuplexAudioManager.setPlayerSampleRate(mOutSampleRate);
                mDuplexAudioManager.setNumPlayerChannels(mOutChannelCount);
                mDuplexAudioManager.setPlayerSharingMode(mTransferType == TRANSFER_MMAP_EXCLUSIVE
                        ? BuilderBase.SHARING_MODE_EXCLUSIVE : BuilderBase.SHARING_MODE_SHARED);
                mDuplexAudioManager.setPlayerPerformanceMode(mOutPerformanceMode);

                // Recorder
                mDuplexAudioManager.setRecorderRouteDevice(mInDeviceInfo);
                mDuplexAudioManager.setInputPreset(mInputPreset);
                mDuplexAudioManager.setRecorderSampleRate(mInSampleRate);
                mDuplexAudioManager.setNumRecorderChannels(mInChannelCount);
                mDuplexAudioManager.setRecorderSharingMode(mTransferType == TRANSFER_MMAP_EXCLUSIVE
                        ? BuilderBase.SHARING_MODE_EXCLUSIVE : BuilderBase.SHARING_MODE_SHARED);
                mDuplexAudioManager.setRecorderPerformanceMode(mInPerformanceMode);

                boolean enableMMAP = mTransferType != TRANSFER_LEGACY;
                Globals.setMMapEnabled(enableMMAP);
                // This should never happen as MMAP TestModules will not get allocated
                // in the case that MMAP isn't supported on the device.
                // See addTestModule() and initialization of
                // mSupportsMMAP and mSupportsMMAPExclusive.
                if (Globals.isMMapEnabled() != enableMMAP) {
                    Log.d(TAG, "  Invalid MMAP request - " + getDescription());
                    Globals.setMMapEnabled(Globals.isMMapSupported());
                    return setTestState(api, TESTSTATUS_CANT_SET_MMAP,
                            new BadMMAPTestState(this, false, false));
                }
                try {
                    // Open the streams.
                    // Note AudioSources and AudioSinks get allocated at this point
                    int status = mDuplexAudioManager.buildStreams(mAudioApi, mAudioApi);
                    if ((status & DuplexAudioManager.DUPLEX_ERROR_CODE)
                            != DuplexAudioManager.DUPLEX_ERROR_NONE) {
                        Log.e(TAG, "  mDuplexAudioManager.buildStreams() failed. status:0x"
                                + Integer.toHexString(status));
                        int processStep = status & DuplexAudioManager.DUPLEX_ERROR_CODE;

                        boolean recorderFailed =
                                (status & DuplexAudioManager.DUPLEX_STREAM_ID
                                        & DuplexAudioManager.DUPLEX_RECORDER) != 0;
                        boolean playerFailed =
                                (status & DuplexAudioManager.DUPLEX_STREAM_ID
                                        & DuplexAudioManager.DUPLEX_PLAYER) != 0;

                        mDuplexAudioManager.unwind();

                        return setTestState(api,
                                (processStep & DuplexAudioManager.DUPLEX_ERR_BUILD)
                                        != DuplexAudioManager.DUPLEX_ERROR_NONE
                                        ? TESTSTATUS_BAD_START : TESTSTATUS_BAD_BUILD,
                                new TestStateData(this, playerFailed, recorderFailed));
                    }
                } finally {
                    // handle the failure here...
                    Globals.setMMapEnabled(Globals.isMMapSupported());
                }

                // (potentially) Adjust AudioSource parameters
                AudioSource audioSource = mSourceProvider.getActiveSource();

                // Set the sample rate for the source (the sample rate for the player gets
                // set in the DuplexAudioManager.Builder.
                audioSource.setSampleRate(mOutSampleRate);

                // Adjust the player frequency to match with the quantized frequency
                // of the analyzer.
                audioSource.setFreq((float) mAnalyzer.getAdjustedFrequency());

                mWaveView.setNumChannels(mInChannelCount);

                // Validate Sharing Mode
                boolean playerSharingModeVerified =
                        mDuplexAudioManager.isSpecifiedPlayerSharingMode();
                boolean recorderSharingModeVerified =
                        mDuplexAudioManager.isSpecifiedRecorderSharingMode();
                if (!playerSharingModeVerified || !recorderSharingModeVerified) {
                    Log.w(TAG, "  Invalid Sharing Mode - " + getDescription());
                    return setTestState(api, TESTSTATUS_BAD_SHARINGMODE,
                            new BadSharingTestState(this,
                                    !playerSharingModeVerified,
                                    !recorderSharingModeVerified));
                }

                // Validate MMAP
                boolean playerIsMMap = false;
                boolean recorderIsMMap = false;
                if (mTransferType != TRANSFER_LEGACY) {
                    Log.w(TAG, "  Invalid Transfer Mode - " + getDescription());
                    // This is (should be) an MMAP stream
                    playerIsMMap = mDuplexAudioManager.isPlayerStreamMMap();
                    recorderIsMMap = mDuplexAudioManager.isRecorderStreamMMap();

                    if (!playerIsMMap && !recorderIsMMap) {
                        Log.w(TAG, "  Neither stream is MMAP - " + getDescription());
                        return setTestState(api, TESTSTATUS_MISMATCH_MMAP,
                                new BadMMAPTestState(this, !playerIsMMap, !recorderIsMMap));
                    }
                }

                int status = mDuplexAudioManager.start();
                boolean recorderFailed =
                        (status & DuplexAudioManager.DUPLEX_STREAM_ID
                                & DuplexAudioManager.DUPLEX_RECORDER) != 0;
                boolean playerFailed =
                        (status & DuplexAudioManager.DUPLEX_STREAM_ID
                                & DuplexAudioManager.DUPLEX_PLAYER) != 0;
                if ((status & DuplexAudioManager.DUPLEX_ERROR_CODE)
                        !=  DuplexAudioManager.DUPLEX_ERROR_NONE)  {
                    Log.e(TAG, "  Couldn't start duplex streams - " + getDescription());
                    mDuplexAudioManager.unwind();

                    return setTestState(api, TESTSTATUS_BAD_START,
                            new TestStateData(this, playerFailed, recorderFailed));
                }

                // Validate routing
                if (!mDuplexAudioManager.validateRouting()) {
                    Log.w(TAG, "  Invalid Routing - " + getDescription());
                    return setTestState(api, TESTSTATUS_BAD_ROUTING,
                            new TestStateData(this, playerFailed, recorderFailed));
                }

                BadMMAPTestState mmapState = null;
                if (mTransferType != TRANSFER_LEGACY && (!playerIsMMap || !recorderIsMMap)) {
                    // asked for MMAP, but at least one route is Legacy
                    Log.w(TAG, "  Both streams aren't MMAP - " + getDescription());
                    mmapState = new BadMMAPTestState(this, !playerIsMMap, !recorderIsMMap);
                }

                return setTestState(api, TESTSTATUS_RUN, mmapState);
            }

            return setTestState(api, TESTSTATUS_NOT_RUN, null);
        }

        int advanceTestPhase(int api) {
            return 0;
        }

        //
        // HTML Reporting
        //
        TextFormatter generateReport(int api, TextFormatter textFormatter) {
            // Description
            textFormatter.openParagraph()
                    .appendText(getDescription());

            // Result
            int state = getTestState(api);
            if (state != TESTSTATUS_NOT_RUN) {
                textFormatter.appendBreak()
                        .openBold()
                        .appendText(getTestStateString(api))
                        .closeBold();
            }

            // Any Data?
            TestResults results = mTestResults[api];    // need this (potentially) below.
            TestStateData stateData = mTestStateData[api];
            if (results != null && stateData != null) {
                textFormatter.appendBreak()
                        .openTextColor("blue")
                        .appendText(stateData.buildErrorString(this))
                        .closeTextColor();
            }

            // Report Error
            String componentString =
                    stateData != null ?  " - " + stateData.buildComponentString() : "";

            if (hasRun(api) && hasError(api)) {
                textFormatter.appendBreak();
                switch (mTestStateCode[api]) {
                    case TESTSTATUS_BAD_START:
                        textFormatter.openTextColor("red");
                        textFormatter.appendText("Error : Couldn't Start Stream" + componentString);
                        textFormatter.closeTextColor();
                        break;
                    case TESTSTATUS_BAD_BUILD:
                        textFormatter.openTextColor("red");
                        textFormatter.appendText("Error : Couldn't Open Stream" + componentString);
                        textFormatter.closeTextColor();
                        break;
                    case TESTSTATUS_BAD_ROUTING:
                        textFormatter.openTextColor("red");
                        textFormatter.appendText("Error : Invalid Route" + componentString);
                        textFormatter.closeTextColor();
                        break;
                    case TESTSTATUS_BAD_ANALYSIS_CHANNEL:
                        textFormatter.openTextColor("red");
                        textFormatter.appendText("Error : Invalid Analysis Channel"
                                + componentString);
                        textFormatter.closeTextColor();
                        break;
                    case TESTSTATUS_CANT_SET_MMAP:
                        textFormatter.openTextColor("red");
                        textFormatter.appendText("Error : Did not set MMAP mode - "
                                + transferTypeToSharingString(mTransferType) + componentString);
                        textFormatter.closeTextColor();
                        break;
                    case TESTSTATUS_MISMATCH_MMAP: {
                        textFormatter.openTextColor("blue");
                        textFormatter.appendText("Note : ");
                        BadMMAPTestState errorData = (BadMMAPTestState) mTestStateData[api];
                        String transferTypeString = transferTypeToSharingString(mTransferType);
                        if (errorData.mPlayerFailed) {
                            textFormatter.appendText(PLAYER_FAILED_TO_GET_STRING
                                    + transferTypeString);
                            textFormatter.appendBreak();
                            textFormatter.appendText(formatOutputAttributes());
                        }
                        if (errorData.mRecorderFailed) {
                            if (errorData.mPlayerFailed) {
                                textFormatter.appendBreak();
                            }
                            textFormatter.appendText(RECORDER_FAILED_TO_GET_STRING
                                    + transferTypeString);
                            textFormatter.appendBreak();
                            textFormatter.appendText(formatInputAttributes());
                        }
                        textFormatter.closeTextColor();
                    }
                        break;
                    case TESTSTATUS_BAD_SHARINGMODE:
                        textFormatter.openTextColor("blue");
                        textFormatter.appendText("Note : ");
                        BadSharingTestState errorData =
                                (BadSharingTestState) mTestStateData[api];
                        String transferTypeString = transferTypeToSharingString(mTransferType);
                        if (errorData.mPlayerFailed) {
                            textFormatter.appendText(PLAYER_FAILED_TO_GET_STRING
                                    + transferTypeString);
                        }
                        if (errorData.mRecorderFailed) {
                            textFormatter.appendText(RECORDER_FAILED_TO_GET_STRING
                                    + transferTypeString);
                        }
                        textFormatter.appendBreak();
                        textFormatter.appendText(formatOutputAttributes());
                        textFormatter.closeTextColor();
                        break;
                }
                textFormatter.closeTextColor();
            }

            // Results Data
            if (results != null) {
                // We (attempted to) run this module. Let's see how it turned out.
                // we can get null here if the test was cancelled
                Locale locale = Locale.getDefault();
                String maxMagString = String.format(
                        locale, "mag:%.5f ", results.mMaxMagnitude);
                String phaseJitterString = String.format(
                        locale, "jitter:%.5f ", results.mPhaseJitter);

                boolean passMagnitude = mAnalysisType == TYPE_SIGNAL_PRESENCE
                        ? results.mMaxMagnitude >= MIN_SIGNAL_PASS_MAGNITUDE
                        : results.mMaxMagnitude <= MAX_XTALK_PASS_MAGNITUDE;

                // Values / Criteria
                // NOTE: The criteria is why the test passed or failed, not what
                // was needed to pass.
                // So, for a cross-talk test, "mag:0.01062 > 0.01000" means that the test
                // failed, because 0.01062 > 0.01000
                textFormatter.appendBreak();
                textFormatter.openTextColor(passMagnitude ? "black" : "red");
                if (mAnalysisType == TYPE_SIGNAL_PRESENCE) {
                    textFormatter.appendText(maxMagString
                            + String.format(locale,
                            passMagnitude ? " >= %.5f " : " < %.5f ",
                            MIN_SIGNAL_PASS_MAGNITUDE));
                } else {
                    textFormatter.appendText(maxMagString
                            + String.format(locale,
                            passMagnitude ? " <= %.5f " : " > %.5f ",
                            MAX_XTALK_PASS_MAGNITUDE));
                }
                textFormatter.closeTextColor();

                if (mAnalysisType == TYPE_SIGNAL_PRESENCE) {
                    // Do we want a threshold value for jitter in crosstalk tests?
                    boolean passJitter =
                            results.mPhaseJitter <= MAX_SIGNAL_PASS_JITTER;
                    textFormatter.openTextColor(passJitter ? "black" : "red");
                    textFormatter.appendText(phaseJitterString
                            + String.format(locale, passJitter ? " <= %.5f" : " > %.5f",
                            MAX_SIGNAL_PASS_JITTER));
                    textFormatter.closeTextColor();
                } else {
                    textFormatter.appendText(phaseJitterString);
                }

                textFormatter.appendBreak();

                // "Prose" status messages
                textFormatter.openItalic();
                if (mAnalysisType == TYPE_SIGNAL_PRESENCE) {
                    if (results.mMaxMagnitude == 0.0) {
                        textFormatter.appendText("Dead Channel?");
                    } else if (results.mMaxMagnitude > 0.0
                            && results.mMaxMagnitude < MIN_SIGNAL_PASS_MAGNITUDE) {
                        textFormatter.appendText("Low Gain or Volume.");
                    } else if (results.mPhaseJitter > MAX_SIGNAL_PASS_JITTER) {
                        // if the signal is absent or really low, the jitter will be high, so
                        // only call out a high jitter if there seems to be a reasonable signal.
                        textFormatter.appendText("Noisy or Corrupt Signal.");
                    }
                } else {
                    // TYPE_SIGNAL_ABSENCE
                    if (results.mMaxMagnitude > MAX_XTALK_PASS_MAGNITUDE) {
                        textFormatter.appendText("Cross Talk Failed. "
                                + "Crossed patch cables on interface?");
                    }
                }
                textFormatter.closeItalic();

                String savedFileMessage = getSavedFileMessage();
                if (savedFileMessage != null) {
                    textFormatter.appendBreak().appendText(savedFileMessage);
                }
            } else {
                // results == null
                textFormatter.appendBreak()
                        .openBold()
                        .appendText("Skipped.")
                        .closeBold();
            }

            textFormatter.closeParagraph();

            return textFormatter;
        }

        //
        // CTS VerifierReportLog stuff
        //
        // ReportLog Schema
        private static final String KEY_TESTDESCRIPTION = "test_description";
        // Output Specification
        private static final String KEY_OUT_DEVICE_TYPE = "out_device_type";
        private static final String KEY_OUT_DEVICE_NAME = "out_device_name";
        private static final String KEY_OUT_DEVICE_RATE = "out_device_rate";
        private static final String KEY_OUT_DEVICE_CHANS = "out_device_chans";

        // Input Specification
        private static final String KEY_IN_DEVICE_TYPE = "in_device_type";
        private static final String KEY_IN_DEVICE_NAME = "in_device_name";
        private static final String KEY_IN_DEVICE_RATE = "in_device_rate";
        private static final String KEY_IN_DEVICE_CHANS = "in_device_chans";
        private static final String KEY_IN_PRESET = "in_preset";

        void generateReportLog(int api) {
            if (!canRun() || mTestResults[api] == null) {
                return;
            }

            CtsVerifierReportLog reportLog = newReportLog();

            // Description
            reportLog.addValue(
                    KEY_TESTDESCRIPTION,
                    getDescription(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Output Specification
            reportLog.addValue(
                    KEY_OUT_DEVICE_NAME,
                    getOutDeviceName(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_TYPE,
                    mOutDeviceType,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_RATE,
                    mOutSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_OUT_DEVICE_CHANS,
                    mOutChannelCount,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Input Specifications
            reportLog.addValue(
                    KEY_IN_DEVICE_NAME,
                    getInDeviceName(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_TYPE,
                    mInDeviceType,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_RATE,
                    mInSampleRate,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_DEVICE_CHANS,
                    mInChannelCount,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IN_PRESET,
                    mInputPreset,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            // Results
            mTestResults[api].generateReportLog(reportLog);

            reportLog.submit();
        }
    }

    /*
     * TestResults
     */
    class TestResults {
        int mApi;
        double mMagnitude;
        double mMaxMagnitude;
        double mPhase;
        double mPhaseJitter;

        TestResults(int api, double magnitude, double maxMagnitude, double phase,
                    double phaseJitter) {
            mApi = api;
            mMagnitude = magnitude;
            mMaxMagnitude = maxMagnitude;
            mPhase = phase;
            mPhaseJitter = phaseJitter;
        }

        // ReportLog Schema
        private static final String KEY_TESTAPI = "test_api";
        private static final String KEY_MAXMAGNITUDE = "max_magnitude";
        private static final String KEY_PHASEJITTER = "phase_jitter";

        void generateReportLog(CtsVerifierReportLog reportLog) {
            reportLog.addValue(
                    KEY_TESTAPI,
                    mApi,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_MAXMAGNITUDE,
                    mMaxMagnitude,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_PHASEJITTER,
                    mPhaseJitter,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }
    }

    abstract void gatherTestModules(TestManager testManager);

    abstract void postValidateTestDevices(int numValidTestModules);

    /*
     * TestManager
     */
    class TestManager {
        static final String TAG = "TestManager";

        // Audio Device Type ID -> TestProfile
        private ArrayList<TestModule> mTestModules = new ArrayList<TestModule>();

        public int mApi;

        private int    mPhaseCount;

        // which route are we running
        static final int TESTSTEP_NONE = -1;
        private int mTestStep = TESTSTEP_NONE;

        private Timer mTimer;

        public void initializeTests() {
            // Get the test modules from the sub-class
            clearTestModules();
            gatherTestModules(this);

            validateTestDevices();
            displayTestDevices();
        }

        public void clearTestState() {
            for (TestModule module: mTestModules) {
                module.clearTestState(mApi);
            }
        }

        public void clearTestModules() {
            mTestModules.clear();
        }

        private void addIndexedTestModule(TestModule module) {
            module.setModuleIndex(mTestModules.size());
            mTestModules.add(module);
        }

        public void addTestModule(TestModule module) {
            // We're going to expand each module to three, one for each transfer type

            //
            // BuilderBase.PERFORMANCE_MODE_NONE
            //
            module.setTransferType(TestModule.TRANSFER_LEGACY);
            // Test Performance Mode None for both Output and Input
            module.mOutPerformanceMode = module.mInPerformanceMode =
                    BuilderBase.PERFORMANCE_MODE_NONE;
            addIndexedTestModule(module);

            //
            // BuilderBase.PERFORMANCE_MODE_LOWLATENCY
            //
            try {
                // Expand out to PerformanceMode.None & PerformanceMode.LowLatency
                TestModule clonedModule = module.clone();
                // Test Performance Mode LowLatency for both Output and Input
                clonedModule.mOutPerformanceMode = clonedModule.mInPerformanceMode =
                        BuilderBase.PERFORMANCE_MODE_LOWLATENCY;
                clonedModule.mSectionTitle = null;
                addIndexedTestModule(clonedModule);
            } catch (CloneNotSupportedException ex) {
                Log.e(TAG, "Couldn't clone TestModule - PERFORMANCE_MODE_LOWLATENCY");
            }

            //
            // MMAP Modes - BuilderBase.PERFORMANCE_MODE_LOWLATENCY
            // Note: Java API doesn't support MMAP Modes
            //
            if (mSupportsMMAP && mApi == TEST_API_NATIVE) {
                try {
                    TestModule moduleMMAP = module.clone();
                    moduleMMAP.setTransferType(TestModule.TRANSFER_MMAP_SHARED);
                    // Test Performance Mode LowLatency for both Output and Input
                    moduleMMAP.mOutPerformanceMode = moduleMMAP.mInPerformanceMode =
                            BuilderBase.PERFORMANCE_MODE_LOWLATENCY;
                    addIndexedTestModule(moduleMMAP);
                    moduleMMAP.mSectionTitle = null;
                } catch (CloneNotSupportedException ex) {
                    Log.e(TAG, "Couldn't clone TestModule - TRANSFER_MMAP_SHARED");
                }
            }

            // Note: Java API doesn't support MMAP Modes
            if (mSupportsMMAPExclusive && mApi == TEST_API_NATIVE) {
                try {
                    TestModule moduleExclusive = module.clone();
                    moduleExclusive.setTransferType(TestModule.TRANSFER_MMAP_EXCLUSIVE);
                    // Test Performance Mode LowLatency for both Output and Input
                    moduleExclusive.mOutPerformanceMode = moduleExclusive.mInPerformanceMode =
                            BuilderBase.PERFORMANCE_MODE_LOWLATENCY;
                    addIndexedTestModule(moduleExclusive);
                    moduleExclusive.mSectionTitle = null;
                } catch (CloneNotSupportedException ex) {
                    Log.e(TAG, "Couldn't clone TestModule - TRANSFER_MMAP_EXCLUSIVE");
                }
            }
        }

        public void validateTestDevices() {
            // do we have the output device we need
            AudioDeviceInfo[] outputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (TestModule testModule : mTestModules) {
                testModule.mOutDeviceInfo = null;
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : outputDevices) {
                    // Don't invalidate previously validated devices
                    // Tests that test multiple device instances (like USB headset/interface)
                    // need to remember what devices are valid after being disconnected
                    // in order to connect the next device instance.
                    if (testModule.mOutDeviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            && !mHasSpeaker) {
                        break;
                    } else if (testModule.mOutDeviceType == devInfo.getType()) {
                        testModule.mOutDeviceInfo = devInfo;
                        break;
                    }
                }
            }

            // do we have the input device we need
            AudioDeviceInfo[] inputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (TestModule testModule : mTestModules) {
                testModule.mInDeviceInfo = null;
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : inputDevices) {
                    // Don't invalidate previously validated devices?
                    // See comment above.
                    if (testModule.mInDeviceType == AudioDeviceInfo.TYPE_BUILTIN_MIC
                            && !mHasMic) {
                        break;
                    } else if (testModule.mInDeviceType == devInfo.getType()) {
                        testModule.mInDeviceInfo = devInfo;
                        break;
                    }
                }
            }

            // Is the Transfer Mode valid for this API?
            for (TestModule testModule : mTestModules) {
                if (mApi == TEST_API_JAVA
                        && testModule.mTransferType != TestModule.TRANSFER_LEGACY) {
                    // MMAP transfer modes are not supported on JAVA
                    testModule.mInDeviceInfo = null;
                    testModule.mOutDeviceInfo = null;
                }
            }

            postValidateTestDevices(countValidTestModules());
        }

        public int getNumTestModules() {
            return mTestModules.size();
        }

        public int countValidTestModules() {
            int numValid = 0;
            for (TestModule testModule : mTestModules) {
                if (testModule.mOutDeviceInfo != null && testModule.mInDeviceInfo != null
                        // ignore MMAP Failures
                        && testModule.mTestStateCode[mApi] != TestModule.TESTSTATUS_MISMATCH_MMAP
                        && testModule.mTestStateCode[mApi]
                            != TestModule.TESTSTATUS_BAD_SHARINGMODE) {
                    numValid++;
                }
            }
            return numValid;
        }

        public int countValidOrPassedTestModules() {
            int numValid = 0;
            for (TestModule testModule : mTestModules) {
                if ((testModule.mOutDeviceInfo != null && testModule.mInDeviceInfo != null)
                        || testModule.hasPassed(mApi)) {
                    numValid++;
                }
            }
            return numValid;
        }

        public int countTestedTestModules() {
            int numTested = 0;
            for (TestModule testModule : mTestModules) {
                if (testModule.hasRun(mApi)) {
                    numTested++;
                }
            }
            return numTested;
        }

        public void displayTestDevices() {
            StringBuilder sb = new StringBuilder();
            sb.append("Tests:");
            int testStep = 0;
            for (TestModule testModule : mTestModules) {
                sb.append("\n");
                if (testModule.getSectionTitle() != null) {
                    sb.append("---" + testModule.getSectionTitle() + "---\n");
                }
                if (testStep == mTestStep) {
                    sb.append(">>>");
                }
                sb.append(testModule.getDescription());

                if (testStep == mTestStep) {
                    sb.append("<<<");
                }

                sb.append(testModule.getTestStateString(mApi));

                String savedFileMessage = testModule.getSavedFileMessage();
                if (savedFileMessage != null) {
                    sb.append("\n").append(savedFileMessage);
                }
                testStep++;
            }
            mRoutesTx.setText(sb.toString());

            showDeviceView();
        }

        public TestModule getActiveTestModule() {
            return mTestStep != TESTSTEP_NONE && mTestStep < mTestModules.size()
                    ? mTestModules.get(mTestStep)
                    : null;
        }

        private int countFailures(int api) {
            int numFailed = 0;
            for (TestModule module : mTestModules) {
                if (module.hasRun(api) // can only fail if it has run
                        && (module.hasError(api) || !module.hasPassed(api))) {
                    // Ignore MMAP "Inconsistencies"
                    // (we didn't get an MMAP stream so we skipped the test)
                    if (module.mTestStateCode[api]
                                != TestModule.TESTSTATUS_MISMATCH_MMAP
                            && module.mTestStateCode[api]
                                != TestModule.TESTSTATUS_BAD_SHARINGMODE) {
                        numFailed++;
                    }
                }
            }
            return numFailed;
        }

        public int startTest(TestModule testModule) {
            if (mTestCanceledByUser) {
                return TestModule.TESTSTATUS_NOT_RUN;
            }

            return testModule.startTestModule(mApi);
        }

        private static final int MS_PER_SEC = 1000;
        private static final int TEST_TIME_IN_SECONDS = 2;
        public void startTest(int api) {
            showDeviceView();

            mApi = api;

            mTestStep = TESTSTEP_NONE;
            mTestCanceledByUser = false;

            mUtiltitiesHandler.setEnabled(false);

            (mTimer = new Timer()).scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    completeTestStep();
                    advanceTestModule();
                }
            }, 0, TEST_TIME_IN_SECONDS * MS_PER_SEC);
        }

        public void stopTest() {
            if (mTestStep != TESTSTEP_NONE) {
                mTestStep = TESTSTEP_NONE;

                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer = null;
                }
                mDuplexAudioManager.unwind();
            }
        }

        protected boolean calculatePass() {
            int numFailures = countFailures(mApi);
            int numUntested = countValidTestModules() - countTestedTestModules();
            return mTestHasBeenRun && !mTestCanceledByUser && numFailures == 0 && numUntested <= 0;
        }

        public void generateResultsText(TextFormatter formatter) {
            formatter.clear();
            formatter.openDocument();

            formatter.openHeading(3)
                    .appendText(getTestCategory())
                    .closeHeading(3);

            mTestManager.generateReport(formatter);

            formatter.openParagraph();
            formatter.appendText("Audio Test Version: " + Common.VERSION_CODE);
            formatter.appendBreak();
            formatter.appendText("Android SDK Version: " + Build.VERSION.SDK_INT);
            formatter.appendBreak();
            formatter.appendText("- " + Build.MANUFACTURER + " - " + Build.MODEL);
            formatter.appendBreak().appendBreak();

            int numFailures = countFailures(mApi);
            int numUntested = getNumTestModules() - countTestedTestModules();
            formatter.appendText("Failure Count: " + numFailures);
            formatter.appendBreak();
            formatter.appendText("Untested Paths: " + numUntested);

            if (numFailures == 0 && numUntested == 0) {
                formatter.appendBreak();
                formatter.appendText("All tests passed.");
            }
            formatter.closeParagraph();

            if (mTestCanceledByUser) {
                formatter.openParagraph();
                formatter.appendText("Test Canceled");
                formatter.appendBreak();

                formatter.openBold()
                        .appendText("Please run the test sequence to completion.")
                        .closeBold()
                        .appendBreak()
                        .appendBreak();
            }

            mTestHasBeenRun = !mTestCanceledByUser;
            boolean passEnabled = passBtnEnabled();
            getPassButton().setEnabled(passEnabled);

            if (passEnabled && numFailures != 0) {
                formatter.appendText("Although not all test modules passed, "
                        + "for this OS version you may press the ");
                formatter.openBold();
                formatter.appendText("PASS");
                formatter.closeBold();
                formatter.appendText(" button.");
                formatter.appendBreak();
                formatter.appendText("Note: In future versions, "
                        + "ALL test modules will be required to pass.");
                formatter.appendBreak();
            }

            if (passEnabled) {
                formatter.appendText("Press the ");
                formatter.openBold();
                formatter.appendText("PASS");
                formatter.closeBold();
                formatter.appendText(" button below to complete the test.");
                formatter.closeParagraph();
            }
            formatter.closeDocument();
        }

        public void completeTest() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    enableTestButtons(true, false);

                    mRoutesTx.setVisibility(View.GONE);
                    mWaveView.setVisibility(View.GONE);

                    generateResultsText(mTextFormatter);
                    mTextFormatter.put(mResultsView);

                    showResultsView();

                    mUtiltitiesHandler.setEnabled(true);
                }
            });
        }

        public void completeTestStep() {
            if (mTestStep != TESTSTEP_NONE) {
                mDuplexAudioManager.unwind();
                // Give the audio system a chance to settle from the previous state
                // It is often the case that the Java API will not route to the specified
                // device if we teardown/startup too quickly. This sleep cirmumvents that.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Log.e(TAG, "sleep failed?");
                }

                TestModule testModule = getActiveTestModule();
                if (testModule != null) {
                    if (testModule.canRun()) {
                        testModule.setTestResults(mApi, mAnalyzer);
                        if (!testModule.hasPassed(mApi)) {
                            String message = saveWaveFile(mAnalyzer, testModule.getModuleIndex());
                            testModule.setSavedFileMessage(message);
                        } else {
                            testModule.setSavedFileMessage(null); // erase any old messages
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                displayTestDevices();
                                mWaveView.resetPersistentMaxMagnitude();
                            }
                        });
                    }
                    testModule.logEnding(mApi);
                }
            }
        }

        public void advanceTestModule() {
            Log.d(TAG, "advanceTestModule() user cancel:" + mTestCanceledByUser);
            if (mTestCanceledByUser) {
                // test shutting down. Bail.
                return;
            }

            while (++mTestStep < mTestModules.size()) {
                // update the display to show progress
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayTestDevices();
                    }
                });

                // Scan until we find a TestModule that starts playing/recording
                TestModule testModule = mTestModules.get(mTestStep);
                Log.d(TAG, " - testModule: " + testModule.getModuleIndex());

                // Don't run if it has already been run. This to preserve (possible) error
                // codes from previous runs
                Log.d(TAG, " - hasRun:" + testModule.hasRun(mApi));
                if (!testModule.hasRun(mApi)) {
                    int status = startTest(testModule);
                    Log.d(TAG, " - status: " + status);
                    if (status == TestModule.TESTSTATUS_RUN) {
                        // Allow this test to run to completion.
                        Log.d(TAG, "Run Test Module:" + testModule.getDescription());
                        break;
                    }
                    Log.d(TAG, "Cancel Test Module:" + testModule.getDescription()
                            + " status:" + testModule.getTestStateString(mApi));
                    // Otherwise, playing/recording failed, look for the next TestModule
                    mDuplexAudioManager.unwind();
                }
            }

            if (mTestStep >= mTestModules.size()) {
                stopTest();
                completeTest();
            }
        }

        TextFormatter generateReport(TextFormatter textFormatter) {
            textFormatter.openHeading(3);
            textFormatter.appendText("Test API: ");
            textFormatter.appendText(audioApiToString(mApi));
            textFormatter.closeHeading(3);

            for (TestModule module : mTestModules) {
                module.generateReport(mApi, textFormatter);
            }

            return textFormatter;
        }

        //
        // CTS VerifierReportLog stuff
        //
        void generateReportLog() {
            int testIndex = 0;
            for (TestModule module : mTestModules) {
                for (int api = TEST_API_NATIVE; api < NUM_TEST_APIS; api++) {
                    module.generateReportLog(api);
                }
            }
        }
    }

    /**
     * @return short name of the physical route
     */
    abstract String getRouteDescription();

    /**
     * Delete all the previously saved WAV files so the user does not
     * debug obsolete data.
     */
    public void deleteOldWaveFiles() {
        if (mRecordingDir.exists()) {
            File[] files = mRecordingDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (!file.delete()) {
                            Log.e(TAG, "Failed to delete file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    private String saveWaveFile(BaseSineAnalyzer mAnalyzer, int index) {
        File waveFile = new File(mRecordingDir,
                String.format(Locale.US, "paths_%s_%03d.wav",
                        getRouteDescription(), index));

        float[] data = mAnalyzer.getRecordedData();
        int numSamples = data.length;
        if (numSamples > 0) {
            try {
                WaveFileWriter writer = new WaveFileWriter(waveFile);
                writer.setFrameRate(mAnalyzer.getSampleRate());
                writer.setBitsPerSample(24);
                writer.write(data);
                writer.close();
                return "Wrote " + numSamples + " samples to " + waveFile.getAbsolutePath();
            } catch (IOException e) {
                return "FAILED to save " + waveFile.getAbsolutePath()
                        + ", " + e.getMessage();
            }
        } else {
            return "No recorded data!";
        }
    }

    //
    // Process Handling
    //
    private void startTest(int api) {
        if (mDuplexAudioManager == null) {
            mDuplexAudioManager = new DuplexAudioManager(null, null);
        }

        enableTestButtons(false, true);
        getPassButton().setEnabled(false);
        deleteOldWaveFiles();

        mTestManager.startTest(api);
    }

    private void stopTest() {
        mTestManager.stopTest();
        mTestManager.displayTestDevices();
    }

    protected boolean calculatePass() {
        return mTestManager.calculatePass();
    }

    protected abstract boolean hasPeripheralSupport();

    boolean passBtnEnabled() {
        return (mTestHasBeenRun && calculatePass())
                || (mTestHasBeenRun && grantAutoPass())
                || !hasPeripheralSupport()
                || !mIsHandheld;
    }

    void displayNonHandheldMessage() {
        mTextFormatter.clear();
        mTextFormatter.openDocument();
        mTextFormatter.openParagraph();
        mTextFormatter.appendText(getResources().getString(R.string.audio_exempt_nonhandheld));
        mTextFormatter.closeParagraph();

        mTextFormatter.closeDocument();
        mTextFormatter.put(mResultsView);
        showResultsView();
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    //
    // CTS VerifierReportLog stuff
    //
    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_AUDIO_DATAPATHS);
    }

    @Override
    public void recordTestResults() {
// TODO Remove all report logging from this file. This is a quick fix.
// This code generates multiple records in the JSON file.
// That duplication is invalid JSON and causes the database
// ingestion to fail.
//        mTestManager.generateReportLog();
    }

    private static String getTimestampString() {
        DateFormat df = DateFormat.getDateTimeInstance();
        Date now = Calendar.getInstance().getTime();
        return "[" + df.format(now) + "]";
    }

    private void shareResults() {
        if (mTextFormatter != null) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/text");

            String subjectText = "CTS Verifier - Results " + getTestCategory()
                    + " " + getTimestampString();

            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subjectText);

            // Regenerate the results in text-only format
            PlainTextFormatter formatter = new PlainTextFormatter();
            mTestManager.generateResultsText(formatter);
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, formatter.toString());

            // Put up the chooser
            startActivity(Intent.createChooser(sharingIntent, "Share using:"));
        }
    }

    //
    // AudioMultiApiActivity Overrides
    //
    @Override
    public void onApiChange(int api) {
        stopTest();
        mTestManager.mApi = api;
        mTestManager.validateTestDevices();
        mResultsView.invalidate();
        mTestHasBeenRun = false;
        getPassButton().setEnabled(passBtnEnabled());

        mTestManager.initializeTests();
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_datapaths_start) {
            startTest(mActiveTestAPI);
        } else if (id == R.id.audio_datapaths_cancel) {
            mTestCanceledByUser = true;
            mTestHasBeenRun = false;
            stopTest();
            mTestManager.completeTest();
        } else if (id == R.id.audio_datapaths_clearresults) {
            mTestManager.clearTestState();
            mTestManager.displayTestDevices();
        } else if (id == R.id.audio_datapaths_shareresults) {
            shareResults();
        } else if (id == R.id.audio_datapaths_showresults) {
            showResultsView();
        } else if (id == R.id.audioJavaApiBtn || id == R.id.audioNativeApiBtn) {
            super.onClick(view);
            mTestCanceledByUser = true;
            stopTest();
            mTestManager.clearTestState();
            showDeviceView();
            mTestManager.displayTestDevices();
        }
    }

    //
    // (MegaAudio) AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        TestModule testModule = mTestManager.getActiveTestModule();
        if (testModule != null) {
            mAnalyzer.analyzeBuffer(audioData, testModule.mInChannelCount, numFrames);
            mWaveView.setPCMFloatBuff(audioData, testModule.mInChannelCount, numFrames);
        }
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        void stateChangeHandler() {
            Log.d(TAG, "  stateChangeHandler()");
            mTestManager.validateTestDevices();
            if (!mIsHandheld) {
                displayNonHandheldMessage();
                getPassButton().setEnabled(true);
            } else {
                showDeviceView();
                mTestManager.displayTestDevices();
                if (mTestHasBeenRun) {
                    getPassButton().setEnabled(passBtnEnabled());
                }
            }
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            Log.d(TAG, "onAudioDevicesAdded()");
            stateChangeHandler();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            Log.d(TAG, "onAudioDevicesRemoved()");
            stateChangeHandler();
        }
    }
}
