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

package com.android.cts.verifier.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;

// MegaAudio
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.Globals;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.common.StreamState;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.OboePlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SilenceAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.OboeRecorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

import java.util.ArrayList;

// @CddTest(requirement = "7.8.2.1/C-1-1,C-1-2,C-1-3,C-1-4,C-2-1")

/**
 * CTS Verifier Test module for AAudio Stream Disconnect events
 */
public class AudioDisconnectActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener {
    private static final String TAG = AudioDisconnectActivity.class.getSimpleName();
    private static final boolean LOG = true;

    private BroadcastReceiver mPluginReceiver = new PluginBroadcastReceiver();

    // MegaAudio
    private OboePlayer mPlayer;
    private OboeRecorder mRecorder;
    private StreamBase  mStream;

    private int mNumExchangeFrames;
    private int mSystemSampleRate;

    // UI
    private TextView mHasPortQueryText;
    private Button mHasAnalogPortYesBtn;
    private Button mHasAnalogPortNoBtn;

    private Button mStartBtn;
    private Button mStopBtn;

    private TextView mUserPromptTx;
    private TextView mDebugMessageTx;
    private TextView mResultsTx;

    // Test State
    private boolean mSupportsHeadset;
    private boolean mIsAudioRunning;
    private volatile int mPlugCount;

    static {
        StreamBase.loadMegaAudioLibrary();
    }

    // Lowlatency/not lowlatency
    // MMAP/not MMAP (legacy i.e. audioflinger).
    // Shared/Exclusive (not legacy)
    class TestConfiguration {
        static final int IO_INPUT = 0;
        static final int IO_OUTPUT = 1;
        int mDirection;
        int mSampleRate;
        int mNumChannels;

        static final int OPTION_NONE = 0x00000000;
        static final int OPTION_LOWLATENCY = 0x00000001;
        static final int OPTION_EXCLUSIVE = 0x00000002;
        static final int OPTION_MMAP = 0x00000004;
        int mOptions;

        static final int RESULT_DETECTED = 0; // i.e. the disconnect notification was received
        static final int RESULT_NOTTESTED = -1;
        static final int RESULT_TIMEOUT = -2;
        static final int RESULT_SKIPPED = -3;
        static final int RESULT_BADDISCONNECTCODE = -4;
        static final int RESULT_BADSTART = -5;
        // Stream Errors
        static final int RESULT_STREAM_OK = 0;
        static final int RESULT_BADSTREAMBUILD = -6;
        static final int RESULT_BADSTREAMOPEN = -7;
        static final int RESULT_BADSTREAMSTART = -8;
        static final int RESULT_BADSTREAMUNKNOWN = -9;

        /*
         * These variables monitor the stream states that we are interested in.
         */
        // Monitore stream start
        int mStreamStartResult;

        // Monitors insertion (plug-in) events.
        int mInsertPlugResult;

        // Monitors that the stream has exited the StreamState.STARTED, i.e. been disconnected
        // state after the user plugs in the headset
        int mInsertStreamDisconnectResult;

        // Checks the value returned from Oboe/AAudio getLastErrorCallbackResult() method
        // after a disconnect is noticed. Makes sure it is ERROR_DISCONNECTED
        int mInsertStreamOboeDisconnectResult;
        int mInsertStreamOboeDisconnectCode;

        // Monitors removal (unplug) events.
        int mRemovalPlugResult;

        // Monitors that the stream has exited the StreamState.STARTED, i.e. been disconnected
        // state after the user unplugs the headset
        int mRemovalStreamDisconnectResult;

        // Checks the value returned from Oboe/AAudio getLastErrorCallbackResult() method
        // after a disconnect is noticed. Makes sure it is ERROR_DISCONNECTED
        int mRemovalStreamOboeDisconnectResult;
        int mRemovalStreamOboeDisconnectCode;

        TestConfiguration(int direction, int sampleRate, int numChannels, int options) {
            mDirection = direction;

            mSampleRate = sampleRate;
            mNumChannels = numChannels;

            mOptions = options;

            mStreamStartResult = RESULT_NOTTESTED;
            mInsertPlugResult = RESULT_NOTTESTED;
            mInsertStreamDisconnectResult = RESULT_NOTTESTED;
            mInsertStreamOboeDisconnectResult = RESULT_NOTTESTED;
            mRemovalPlugResult = RESULT_NOTTESTED;
            mRemovalStreamDisconnectResult = RESULT_NOTTESTED;
            mRemovalStreamOboeDisconnectResult = RESULT_NOTTESTED;
        }

        boolean isLowLatency() {
            return (mOptions & OPTION_LOWLATENCY) != 0;
        }

        boolean isExclusive() {
            return (mOptions & OPTION_EXCLUSIVE) != 0;
        }

        boolean isMMap() {
            return (mOptions & OPTION_MMAP) != 0;
        }

        static String resultToString(int resultCode) {
            switch (resultCode) {
                case RESULT_NOTTESTED:
                    return "NOT TESTED";

                case RESULT_TIMEOUT:
                    return "TIME OUT";

                case RESULT_SKIPPED:
                    return "SKIPPED";

                case RESULT_DETECTED:
                    return "OK";

                case RESULT_BADDISCONNECTCODE:
                    return "BAD DISCONNECT CODE";

                case RESULT_BADSTART:
                    return "BAD AUDIO START";

                case RESULT_BADSTREAMBUILD:
                    return "BAD STREAM BUILD";

                case RESULT_BADSTREAMOPEN:
                    return "BAD STREAM OPEN";

                case RESULT_BADSTREAMSTART:
                    return "BAD STREAM START";

                default:
                    return "??";
            }
        }

        public String getConfigString() {
            return "" + (mDirection == TestConfiguration.IO_INPUT ? "IN" : "OUT")
                    + " " + mSampleRate + " " + mNumChannels
                    + (isLowLatency() ? " Low Latency" : "")
                    + (isExclusive() ? " Exclusive" : "")
                    + (isMMap() ? " MMAP" : "");
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("-----------\n");
            sb.append(getConfigString() + "\n");
            sb.append("insert: " + resultToString(mInsertPlugResult) + "\n"
                    + "stream disconnect: " + resultToString(mInsertStreamDisconnectResult) + "\n"
                    + "disconnect code: " + resultToString(mInsertStreamOboeDisconnectResult)
                    + "\n"
                    + "remove: " + resultToString(mRemovalPlugResult) + "\n"
                    + "stream disconnect: " + resultToString(mRemovalStreamDisconnectResult) + "\n"
                    + "disconnect code: " + resultToString(mRemovalStreamOboeDisconnectResult)
                    + "\n");

            return sb.toString();
        }

        boolean isPass() {
            return mStreamStartResult == RESULT_STREAM_OK
                    && (mInsertPlugResult == RESULT_DETECTED
                        || mInsertPlugResult == RESULT_SKIPPED)
                    && (mInsertStreamDisconnectResult == RESULT_DETECTED
                        || mInsertStreamDisconnectResult == RESULT_SKIPPED)
                    && (mInsertStreamOboeDisconnectResult == RESULT_DETECTED
                        || mInsertStreamOboeDisconnectResult == RESULT_SKIPPED)
                    && (mRemovalPlugResult == RESULT_DETECTED
                        || mRemovalPlugResult == RESULT_SKIPPED)
                    && (mRemovalStreamDisconnectResult == RESULT_DETECTED
                        || mRemovalStreamDisconnectResult == RESULT_SKIPPED)
                    && (mRemovalStreamOboeDisconnectResult == RESULT_DETECTED
                        || mRemovalStreamOboeDisconnectResult == RESULT_SKIPPED);
        }

        void setSkipped() {
            mInsertPlugResult = RESULT_SKIPPED;
            mInsertStreamDisconnectResult = RESULT_SKIPPED;
            mInsertStreamOboeDisconnectResult = RESULT_SKIPPED;
            mRemovalPlugResult = RESULT_SKIPPED;
            mRemovalStreamDisconnectResult = RESULT_SKIPPED;
            mRemovalStreamOboeDisconnectResult = RESULT_SKIPPED;
        }

        String buildErrorString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Failed Configuration: " + getConfigString());

            // Prose Description
            // move this string build into TestConfiguration class
            sb.append("\n\n");
            if (mInsertPlugResult == TestConfiguration.RESULT_TIMEOUT) {
                sb.append(getString(R.string.audio_disconnect_insert_fail_prose));
            } else if (mInsertStreamDisconnectResult
                    == TestConfiguration.RESULT_TIMEOUT) {
                sb.append(getString(R.string.audio_stream_disconnect_insert_fail_prose));
            } else if (mInsertStreamOboeDisconnectResult
                    == TestConfiguration.RESULT_BADDISCONNECTCODE) {
                sb.append(getString(R.string.audio_stream_disconnect_code_insert_fail_prose)
                        + " code:" + mInsertStreamOboeDisconnectCode);
            } else if (mRemovalPlugResult == TestConfiguration.RESULT_TIMEOUT) {
                sb.append(getString(R.string.audio_disconnect_remove_fail_prose));
            } else if (mRemovalStreamDisconnectResult
                    == TestConfiguration.RESULT_TIMEOUT) {
                sb.append(getString(R.string.audio_stream_disconnect_remove_fail_prose));
            } else if (mRemovalStreamOboeDisconnectResult
                    == TestConfiguration.RESULT_BADDISCONNECTCODE) {
                sb.append(getString(R.string.audio_stream_disconnect_code_remove_fail_prose)
                        + " code:" + mRemovalStreamOboeDisconnectCode);
            } else if (mStreamStartResult == TestConfiguration.RESULT_BADSTREAMBUILD) {
                sb.append("Stream Build Error: " + mStreamStartResult);
            } else if (mStreamStartResult == TestConfiguration.RESULT_BADSTREAMOPEN) {
                sb.append("Stream Open Error: " + mStreamStartResult);
            } else if (mStreamStartResult == TestConfiguration.RESULT_BADSTREAMSTART) {
                sb.append("Stream Start Error: " + mStreamStartResult);
            } else if (mStream != null) {
                sb.append("Other Error: " + StreamState.toString(mStream.getStreamState()));
            } else {
                sb.append("No Stream.");
            }

            return sb.toString();
        }
    }

    private ArrayList<TestConfiguration> mTestConfigs = new ArrayList<TestConfiguration>();

    void setTestConfigs() {
//        This logic will cover the four main data paths.
//        if (isMMapSupported) {
//            LOWLATENCY + MMAP + EXCLUSIVE
//            LOWLATENCY + MMAP // shared
//        }
//        LOWLATENCY // legacy
//        NONE

        // Player
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                mSystemSampleRate, 2,
                TestConfiguration.OPTION_LOWLATENCY));
        if (Globals.isMMapSupported()) {
            mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                    mSystemSampleRate, 2,
                    TestConfiguration.OPTION_LOWLATENCY
                            | TestConfiguration.OPTION_MMAP));
            mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                    mSystemSampleRate, 2,
                    TestConfiguration.OPTION_LOWLATENCY
                            | TestConfiguration.OPTION_MMAP
                            | TestConfiguration.OPTION_EXCLUSIVE));
        }
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_OUTPUT,
                mSystemSampleRate, 2,
                TestConfiguration.OPTION_NONE));

        // Recorder
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                mSystemSampleRate, 1,
                TestConfiguration.OPTION_LOWLATENCY));
        if (Globals.isMMapSupported()) {
            mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                    mSystemSampleRate, 1,
                    TestConfiguration.OPTION_LOWLATENCY
                            | TestConfiguration.OPTION_MMAP));
            mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                    mSystemSampleRate, 1,
                    TestConfiguration.OPTION_LOWLATENCY
                            | TestConfiguration.OPTION_MMAP
                            | TestConfiguration.OPTION_EXCLUSIVE));
        }
        mTestConfigs.add(new TestConfiguration(TestConfiguration.IO_INPUT,
                mSystemSampleRate, 1,
                TestConfiguration.OPTION_NONE));
    }

    void resetTestConfigs() {
        for (TestConfiguration testConfig : mTestConfigs) {
            testConfig.mStreamStartResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mInsertPlugResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mInsertStreamDisconnectResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mInsertStreamOboeDisconnectResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mRemovalPlugResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mRemovalStreamDisconnectResult = TestConfiguration.RESULT_NOTTESTED;
            testConfig.mRemovalStreamOboeDisconnectResult = TestConfiguration.RESULT_NOTTESTED;
        }
    }

    void setTextMessage(TextView textView, String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(message);
            }
        });
    }

    int countToSeconds(int count) {
        float secondsPerTick = (float) POLL_DURATION_MILLIS / 1000.0f;
        return (int) ((float) count * secondsPerTick);
    }

    void showResults(boolean passed) {
        String passStr = getString(
                passed ? R.string.audio_general_teststatus_pass
                        : R.string.audio_general_teststatus_fail);
        mUserPromptTx.setText(passStr);

        // Find the failed module
        TestConfiguration failedConfig = findFailedConfiguration();
        if (failedConfig != null) {
            String errorStr = failedConfig.buildErrorString();
            mDebugMessageTx.setText(errorStr);
        }
    }

    class Tester implements Runnable {
        private void runTest() {
            boolean abortTest = false;
            int timeoutCount;
            mPlugCount = 0;

            if (LOG) {
                Log.d(TAG, "runTest()...");
            }

            //---------------------------
            // Step through each test configuration
            //---------------------------
            for (int testConfigIndex = 0;
                    testConfigIndex < mTestConfigs.size();
                    testConfigIndex++) {
                TestConfiguration testConfig = mTestConfigs.get(testConfigIndex);
                if (LOG) {
                    Log.d(TAG, "testConfig:" + testConfig.getConfigString());
                }
                // What kind of stream are we testing here...
                String streamName = testConfig.mDirection == TestConfiguration.IO_OUTPUT
                        ? "OUTPUT" : "INPUT";

                if (testConfig.isMMap() || testConfig.isExclusive()) {
                    if (!Globals.isMMapSupported()) {
                        testConfig.setSkipped();
                        continue;
                    }
                }

                //-----------------------------------------
                // Start Audio
                //-----------------------------------------
                if (!startAudio(testConfig)) {
                    break;  // ERROR
                }

                setTextMessage(mUserPromptTx, "Waiting for " + streamName + " to start.");
                try {
                    int oldPlugCount;
                    int error;

                    //-------------------------------------------
                    // 1. Wait for stream to start...
                    //-------------------------------------------
                    if (LOG) {
                        Log.d(TAG, "Wait for stream to start...");
                    }
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && timeoutCount-- > 0
                            && mStream.getStreamState() != StreamState.STARTED) {
                        setTextMessage(mDebugMessageTx,
                                "Stream Config: " + testConfig.getConfigString() + "\n"
                                + "Waiting for " + streamName + " to start. state:"
                                + StreamState.toString(mStream.getStreamState())
                                + " seconds:" + countToSeconds(timeoutCount));
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx,
                                "TIMEOUT waiting for " + streamName + " to start");
                        abortTest = true;
                        break;
                    }

                    if (LOG) {
                        Log.d(TAG, "stream started.");
                    }

                    //------------------------------------------
                    // 2. Prompt for headset connect
                    //------------------------------------------
                    setTextMessage(mUserPromptTx, "Insert headset now!");

                    //-------------------------------------------------------------
                    // Wait for plug count to change (i.e. the insert has happened)
                    //-------------------------------------------------------------
                    if (LOG) {
                        Log.d(TAG, "Wait for headset connect");
                    }
                    oldPlugCount = mPlugCount;
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && timeoutCount-- > 0 && mPlugCount == oldPlugCount) {
                        setTextMessage(mDebugMessageTx,
                                "Stream Config: " + testConfig.getConfigString() + "\n"
                                + "Waiting for insert event"
                                + " seconds: " + countToSeconds(timeoutCount));
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx,
                                "TIMEOUT waiting for " + streamName + " insert");
                        testConfig.mInsertPlugResult = TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;  // Done. Test failed.
                    }

                    //-------------------------------------------------------
                    // Got an insert event.
                    //-------------------------------------------------------
                    if (LOG) {
                        Log.d(TAG, "Received plug event.");
                    }
                    testConfig.mInsertPlugResult = TestConfiguration.RESULT_DETECTED;

                    //-----------------------------------------------------------------------
                    // 3. Wait for stream to disconnect after the insert event.
                    //-----------------------------------------------------------------------
                    // This happens really fast, so it is what causes the quick flash
                    // between insert/remove prompts
                    if (LOG) {
                        Log.d(TAG, "Wait for stream disconnect from plug in event");
                    }
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && (timeoutCount-- > 0)
                            && mStream.getStreamState() == StreamState.STARTED) {
                        setTextMessage(mDebugMessageTx,
                                "Stream Config: " + testConfig.getConfigString() + "\n"
                                + "Waiting for " + streamName + " DISCONNECT. state:"
                                + StreamState.toString(mStream.getStreamState())
                                + " seconds:" + countToSeconds(timeoutCount));
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx,
                                "TIMEOUT waiting for DISCONNECT on " + streamName);
                        testConfig.mInsertPlugResult = TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;
                    } // Done. Test failed
                    testConfig.mInsertStreamDisconnectResult = TestConfiguration.RESULT_DETECTED;

                    // The stream is no longer in the STARTED state at this point.
                    error = mStream.getLastErrorCallbackResult();
                    if (LOG) {
                        Log.d(TAG, "plug in getLastErrorCallbackResult() = " + error);
                    }
                    if (error != OboePlayer.ERROR_DISCONNECTED) {
                        testConfig.mInsertStreamOboeDisconnectResult =
                                TestConfiguration.RESULT_BADDISCONNECTCODE;
                        testConfig.mInsertStreamOboeDisconnectCode = error;
                        abortTest = true;
                        break; // Done. Test failed
                    }
                    testConfig.mInsertStreamOboeDisconnectResult =
                            TestConfiguration.RESULT_DETECTED;

                    if (LOG) {
                        Log.d(TAG, "Stream disconnect (post plug in) detected.");
                    }

                    //------------------------------------------------
                    // need to restart the stream after the rerouting
                    //------------------------------------------------
                    restartAudio(testConfig);

                    //-------------------------------------------
                    // 4. Wait for stream to (re)start...
                    //-------------------------------------------
                    if (LOG) {
                        Log.d(TAG, "Wait for stream to restart...");
                    }
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && timeoutCount-- > 0
                            && mStream.getStreamState() != StreamState.STARTED) {
                        setTextMessage(mDebugMessageTx,
                                "Stream Config: " + testConfig.getConfigString() + "\n"
                                        + "Waiting for " + streamName + " to restart. state:"
                                        + StreamState.toString(mStream.getStreamState())
                                        + " seconds:" + countToSeconds(timeoutCount));
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx,
                                "TIMEOUT waiting for " + streamName + " to restart");
                        abortTest = true;
                        break;
                    }

                    if (LOG) {
                        Log.d(TAG, "stream started.");
                    }

                    //--------------------------------
                    // 5. Prompt for headset Remove
                    //--------------------------------
                    setTextMessage(mUserPromptTx, "Remove headset now!");

                    //------------------------------------------------------------------
                    // Wait for plug count to change  (i.e. the removal has happened)
                    //------------------------------------------------------------------
                    if (LOG) {
                        Log.d(TAG, "Wait for removal event.");
                    }
                    oldPlugCount = mPlugCount;
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && timeoutCount-- > 0 && mPlugCount == oldPlugCount) {
                        setTextMessage(mDebugMessageTx,
                                "Stream Config: " + testConfig.getConfigString() + "\n"
                                +  "Waiting for remove event "
                                + " seconds: " + countToSeconds(timeoutCount));
                        Thread.sleep(POLL_DURATION_MILLIS);
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx,
                                "TIMEOUT waiting for " + streamName + " unplug event");
                        testConfig.mRemovalPlugResult = TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;
                    }

                    if (LOG) {
                        Log.d(TAG, "Removal event detected.");
                    }
                    testConfig.mRemovalPlugResult = TestConfiguration.RESULT_DETECTED;

                    //------------------------------------------------------------
                    // 6. Wait for stream disconnect after the removal event.
                    //------------------------------------------------------------
                    // This happens really fast, so it is what causes the quick flash
                    // between insert/remove prompts
                    if (LOG) {
                        Log.d(TAG, "Wait for stream disconnec from unplug in event");
                    }
                    timeoutCount = TIME_TO_FAILURE_MILLIS / POLL_DURATION_MILLIS;
                    while (!abortTest && (timeoutCount > 0)
                            && mStream.getStreamState() == StreamState.STARTED) {
                        setTextMessage(mDebugMessageTx,
                                "Stream Config: " + testConfig.getConfigString() + "\n"
                                + "Waiting for " + streamName + " DISCONNECT. state:"
                                + StreamState.toString(mStream.getStreamState())
                                + " seconds:" + countToSeconds(timeoutCount));
                        Thread.sleep(POLL_DURATION_MILLIS);
                        timeoutCount--;
                    }
                    if (timeoutCount <= 0) {
                        setTextMessage(mUserPromptTx,
                                "TIMEOUT waiting " + streamName + " for DISCONNECT");
                        testConfig.mRemovalStreamDisconnectResult =
                                TestConfiguration.RESULT_TIMEOUT;
                        abortTest = true;
                        break;  // Done. Test Failed.
                    }

                    // Stream is no longer in a STARTED state
                    error = mStream.getLastErrorCallbackResult();
                    if (LOG) {
                        Log.d(TAG, "unplug getLastErrorCallbackResult() = " + error);
                    }
                    if (error != OboePlayer.ERROR_DISCONNECTED) {
                        testConfig.mRemovalStreamOboeDisconnectResult =
                                TestConfiguration.RESULT_BADDISCONNECTCODE;
                        testConfig.mRemovalStreamOboeDisconnectCode = error;
                        abortTest = true;
                        break;  // Done. Test Failed.
                    }
                    testConfig.mRemovalStreamOboeDisconnectResult =
                            TestConfiguration.RESULT_DETECTED;

                    if (LOG) {
                        Log.d(TAG, "Stream disconnect (post unplug) detected.");
                    }
                    testConfig.mRemovalStreamDisconnectResult =
                            TestConfiguration.RESULT_DETECTED;

                } catch (InterruptedException ex) {
                    Log.e(TAG, "InterruptedException: ex:", ex);
                    abortTest = true;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showTestResults();
                    }
                });
                stopAudio();
            } // for looping over the TestConfigurations

            endTest();
        }

        public void run() {
            runTest();
        }
    }

    //
    // Test Process
    //
    void startTest() {
        resetTestConfigs();

        enableTestButtons(false, true);

        (new Thread(new Tester())).start();
    }

    void endTest() {
        if (LOG) {
            Log.d(TAG, "endTest()");
        }
        showTestResults();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDebugMessageTx.setText("");
                enableTestButtons(true, false);
                boolean passed = calcTestPass();
                showResults(passed);
                getPassButton().setEnabled(passed);
            }
        });
    }

    void showTestResults() {
        StringBuilder sb = new StringBuilder();

        sb.append("Test Results:\n");
        for (TestConfiguration testConfig : mTestConfigs) {
            if (testConfig.mInsertPlugResult != TestConfiguration.RESULT_NOTTESTED
                    || testConfig.mRemovalPlugResult != TestConfiguration.RESULT_NOTTESTED) {
                sb.append(testConfig.toString());
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResultsTx.setText(sb.toString());
            }
        });
    }

    boolean calcTestPass() {
        for (TestConfiguration testConfig : mTestConfigs) {
            if (!testConfig.isPass()) {
                return false;
            }
        }
        return true;
    }

    TestConfiguration findFailedConfiguration() {
        for (TestConfiguration testConfig : mTestConfigs) {
            if (!testConfig.isPass()) {
                return testConfig;
            }
        }
        return null;
    }

    public AudioDisconnectActivity() {
        super();
    }

    // Test Phases
    private static final int TESTPHASE_NONE = -1;
    private static final int TESTPHASE_WAITFORSTART = 0;
    private static final int TESTPHASE_WAITFORCONNECT = 1;
    private int mTestPhase = TESTPHASE_NONE;

    // Test Parameters
    public static final int POLL_DURATION_MILLIS = 50;
    public static final int TIME_TO_FAILURE_MILLIS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_disconnect_activity);
        super.onCreate(savedInstanceState);

        setInfoResources(R.string.audio_disconnect_test, R.string.audio_disconnect_info, -1);

        // Analog Port?
        mHasPortQueryText = (TextView) findViewById(R.id.analog_headset_query);
        mHasAnalogPortYesBtn = (Button) findViewById(R.id.headset_analog_port_yes);
        mHasAnalogPortYesBtn.setOnClickListener(this);
        mHasAnalogPortNoBtn = (Button) findViewById(R.id.headset_analog_port_no);
        mHasAnalogPortNoBtn.setOnClickListener(this);

        (mStartBtn = (Button) findViewById(R.id.connection_start_btn)).setOnClickListener(this);
        (mStopBtn = (Button) findViewById(R.id.connection_stop_btn)).setOnClickListener(this);

        mUserPromptTx = (TextView) findViewById(R.id.user_prompt_tx);
        mDebugMessageTx = (TextView) findViewById(R.id.debug_message_tx);
        mResultsTx = (TextView) findViewById(R.id.results_tx);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        StreamBase.setup(this);
        mSystemSampleRate = StreamBase.getSystemSampleRate();
        mNumExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);

        setTestConfigs();

        // do we have to ask if there headset support, or...
        // do we know for sure that there IS headset support?, or...
        mSupportsHeadset =
                (AudioDeviceUtils.supportsAnalogHeadset(this)
                        == AudioDeviceUtils.SUPPORTSDEVICE_YES)
                || (AudioDeviceUtils.supportsUsbHeadset(this)
                        == AudioDeviceUtils.SUPPORTSDEVICE_YES);
        // do we know for sure that there IS NOT headset support?, or...
        boolean doesntSupportHeadset =
                (AudioDeviceUtils.supportsAnalogHeadset(this)
                        == AudioDeviceUtils.SUPPORTSDEVICE_NO)
                        && (AudioDeviceUtils.supportsUsbHeadset(this)
                        == AudioDeviceUtils.SUPPORTSDEVICE_NO);

        // only leave the headset prompt UI on if we don't know about headset support.
        if (mSupportsHeadset || doesntSupportHeadset) {
            hideHasHeadsetUI();
        }

        enableTestButtons(mSupportsHeadset, false);

        if (doesntSupportHeadset) {
            String noSupportString = getString(R.string.analog_headset_port_notsupported);
            String passStr = getString(R.string.audio_general_teststatus_pass);
            mUserPromptTx.setText(noSupportString + " " + passStr);
            getPassButton().setEnabled(true);
        } else {
            mDebugMessageTx.setText(R.string.audio_disconnect_removebeforestart);
        }
        DisplayUtils.setKeepScreenOn(this, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        this.registerReceiver(mPluginReceiver, filter);
    }

    @Override
    public void onPause() {
        stopAudio();

        this.unregisterReceiver(mPluginReceiver);
        super.onPause();
    }

    //
    // PassFailButtons Overrides
    //
    private boolean startAudio(TestConfiguration config) {
        if (LOG) {
            Log.d(TAG, "startAudio()...");
        }
        stopAudio();
        Globals.setMMapEnabled(config.isMMap());

        int buildResult = StreamBase.OK;
        int openResult = StreamBase.OK;
        int startResult = StreamBase.OK;
        if (config.mDirection == TestConfiguration.IO_OUTPUT) {
            AudioSourceProvider sourceProvider = new SilenceAudioSourceProvider();
            try {
                PlayerBuilder playerBuilder = new PlayerBuilder();
                playerBuilder.setPerformanceMode(config.isLowLatency()
                        ? BuilderBase.PERFORMANCE_MODE_LOWLATENCY
                        : BuilderBase.PERFORMANCE_MODE_NONE);
                playerBuilder.setSharingMode(config.isExclusive()
                                ? BuilderBase.SHARING_MODE_EXCLUSIVE
                                : BuilderBase.SHARING_MODE_SHARED);
                playerBuilder.setChannelCount(config.mNumChannels);
                playerBuilder.setSampleRate(config.mSampleRate);
                playerBuilder.setSourceProvider(sourceProvider);
                playerBuilder.setPlayerType(BuilderBase.TYPE_OBOE);
                mPlayer = (OboePlayer) playerBuilder.allocStream();
                if ((buildResult = mPlayer.build(playerBuilder)) == StreamBase.OK
                        && (openResult = mPlayer.open()) == StreamBase.OK
                        && (startResult = mPlayer.start()) == StreamBase.OK) {
                    mIsAudioRunning = true;
                    mStream = mPlayer;
                } else {
                    mIsAudioRunning = false;
                    config.mInsertPlugResult = TestConfiguration.RESULT_BADSTART;
                    mPlayer.unwind();
                    mPlayer = null;
                }
            } catch (PlayerBuilder.BadStateException badStateException) {
                Log.e(TAG, "BadStateException: " + badStateException);
                mIsAudioRunning = false;
                config.mInsertPlugResult = TestConfiguration.RESULT_BADSTART;
            }
        } else {
            AudioSinkProvider sinkProvider = new NopAudioSinkProvider();
            try {
                RecorderBuilder recorderBuilder = new RecorderBuilder();
                recorderBuilder.setRecorderType(BuilderBase.TYPE_OBOE);
                recorderBuilder.setAudioSinkProvider(sinkProvider);
                recorderBuilder.setChannelCount(config.mNumChannels);
                recorderBuilder.setSampleRate(config.mSampleRate);
                recorderBuilder.setChannelCount(config.mNumChannels);
                recorderBuilder.setNumExchangeFrames(mNumExchangeFrames);
                recorderBuilder.setPerformanceMode(config.isLowLatency()
                        ? BuilderBase.PERFORMANCE_MODE_LOWLATENCY
                        : BuilderBase.PERFORMANCE_MODE_NONE);
                recorderBuilder.setSharingMode(config.isExclusive()
                        ? BuilderBase.SHARING_MODE_EXCLUSIVE
                        : BuilderBase.SHARING_MODE_SHARED);
                mRecorder = (OboeRecorder) recorderBuilder.allocStream();
                if ((buildResult = mRecorder.build(recorderBuilder)) == StreamBase.OK
                        && (openResult = mRecorder.open()) == StreamBase.OK
                        && (startResult = mRecorder.start()) == StreamBase.OK) {
                    mIsAudioRunning = true;
                    mStream = mRecorder;
                } else {
                    mIsAudioRunning = false;
                    config.mInsertPlugResult = TestConfiguration.RESULT_BADSTART;
                    mRecorder.unwind();
                    mRecorder = null;
                }
            } catch (RecorderBuilder.BadStateException badStateException) {
                Log.e(TAG, "BadStateException: " + badStateException);
                mIsAudioRunning = false;
                config.mInsertPlugResult = TestConfiguration.RESULT_BADSTART;
            }
        }
        if (mIsAudioRunning) {
            Globals.setMMapEnabled(Globals.isMMapSupported());
            config.mStreamStartResult = TestConfiguration.RESULT_STREAM_OK;
        } else {
            if (buildResult != StreamBase.OK) {
                config.mStreamStartResult = TestConfiguration.RESULT_BADSTREAMBUILD;
            } else if (openResult != StreamBase.OK) {
                config.mStreamStartResult = TestConfiguration.RESULT_BADSTREAMOPEN;
            } else if (startResult != StreamBase.OK) {
                config.mStreamStartResult = TestConfiguration.RESULT_BADSTREAMSTART;
            } else {
                config.mStreamStartResult = TestConfiguration.RESULT_BADSTREAMUNKNOWN;
            }
        }

        if (LOG) {
            Log.d(TAG, "  mIsAudioRunning: " + mIsAudioRunning);
        }
        return mIsAudioRunning;
    }

    private boolean restartAudio(TestConfiguration config) {
        mIsAudioRunning = false;
        return startAudio(config);
    }

    private void stopAudio() {
        if (LOG) {
            Log.d(TAG, "stopAudio()");
        }
        if (!mIsAudioRunning) {
            return; // nothing to do
        }

        if (mPlayer != null) {
            // The stop will happen here along with the rest of the teardown
            mPlayer.unwind();
            mPlayer = null;
        }

        if (mRecorder != null) {
            // The stop will happen here along with the rest of the teardown
            mRecorder.unwind();
            mRecorder = null;
        }

        mIsAudioRunning = false;
    }

    void enableTestButtons(boolean start, boolean stop) {
        mStartBtn.setEnabled(start);
        mStopBtn.setEnabled(stop);
    }

    void hideHasHeadsetUI() {
        mHasPortQueryText.setText(
                mSupportsHeadset ? getString(R.string.analog_headset_port_detected) : "");
        mHasAnalogPortYesBtn.setVisibility(View.GONE);
        mHasAnalogPortNoBtn.setVisibility(View.GONE);
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.headset_analog_port_yes) {
            enableTestButtons(true, false);
        } else if (id == R.id.headset_analog_port_no) {
            String passStr = getString(R.string.audio_general_teststatus_pass);
            mUserPromptTx.setText(passStr);
            getPassButton().setEnabled(true);
            enableTestButtons(false, false);
        } else if (id == R.id.connection_start_btn) {
            mResultsTx.setText("");
            startTest();
        } else if (id == R.id.connection_stop_btn) {
            stopAudio();
        }
    }

    /**
     * Receive a broadcast Intent when a headset is plugged in or unplugged.
     * Display a count on screen.
     */
    public class PluginBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //TODO - We won't need this !mHasHeadset logic when we can be sure
            // the AudioManager.getSupportedDeviceTypes() method is universally available
            if (!mSupportsHeadset) {
                mSupportsHeadset = true;
                hideHasHeadsetUI();
                enableTestButtons(true, false);
            }
            mPlugCount++;
        }
    }
}
