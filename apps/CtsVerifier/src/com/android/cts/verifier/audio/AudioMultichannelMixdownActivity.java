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

package com.android.cts.verifier.audio;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

// CTS Verifier
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.analyzers.BaseSineAnalyzer;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.AudioUtils;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;
import com.android.cts.verifier.libs.ui.HtmlFormatter;
import com.android.cts.verifier.libs.ui.PlainTextFormatter;
import com.android.cts.verifier.libs.ui.TextFormatter;

// MegaAudio
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSource;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CtsVerifier Multichannel Mixdown Test
 */
public class AudioMultichannelMixdownActivity
        extends AudioMultiApiActivity
        implements View.OnClickListener, AppCallback {
    private static final String TAG = "AudioMultichannelMixdownActivity";

    private Context mContext;
    protected AudioManager mAudioManager;

    AudioDeviceConnectionCallback mConnectionListener;

    // UI
    private View mCalibrateAudioButton;
    private View mAudioDevicesButton;

    private View mStartButton;
    private View mStopButton;
    private View mClearResultsButton;

    private RadioButton mSpeakerMicButton;
    private RadioButton mAnalogJackButton;
    private RadioButton mUsbInterfaceButton;
    private RadioButton mUsbHeadsetButton;

    private WaveScopeView mWaveView = null;

    private TextView mPhasesView;

    private View mResultsView;

    private TextFormatter mTextFormatter;

    protected boolean mIsHandheld;

    private boolean mSpeakerMicRequired;
    private boolean mAnalogJackRequired;
    private boolean mUsbInterfaceRequired;
    private boolean mUsbHeadsetRequired;

    private boolean mSpeakerMicRun;
    private boolean mAnalogJackRun;
    private boolean mUsbInterfaceRun;
    private boolean mUsbHeadsetRun;

    // Routes
    // - USB Interface (w/loopback)
    AudioDeviceInfo mUsbInterfaceOut;
    AudioDeviceInfo mUsbInterfaceIn;
    // - USB Headset (w/funplug)
    AudioDeviceInfo mUsbHeadsetOut;
    AudioDeviceInfo mUsbHeadsetIn;
    // - Analog Headset (w/funplug)
    AudioDeviceInfo mAnalogHeadsetOut;
    AudioDeviceInfo mAnalogHeadsetIn;
    // - Speaker/Mic
    AudioDeviceInfo mSpeakerOut;
    AudioDeviceInfo mMicrophoneIn;

    AudioDeviceInfo mSelectedOutput;
    AudioDeviceInfo mSelectedInput;

    // Duplex IO Specs
    private static final int OUT_SAMPLE_RATE = 48000;
    private static final int IN_SAMPLE_RATE = 48000;

    private static final int IN_CHANNEL_COUNT = 2;
    private static final int IN_CHANNEL_LEFT = 0;
    private static final int IN_CHANNEL_RIGHT = 1;

    private int mTestPhase = TestManager.TESTPHASE_NONE;
    private boolean mIsDuplexRunning;

    private static final float MS_PER_SEC = 1000;
    private static final float TEST_TIME_IN_SECONDS = 0.5f;

    private Timer mTimer;

    // Analysis
    // Pass/Fail criteria (with default)
    static final double MIN_SIGNAL_PASS_MAGNITUDE = 0.01;
    static final double MAX_SIGNAL_PASS_JITTER = 0.1;

    // an analyzer per channel
    private BaseSineAnalyzer[] mAnalyzers = new BaseSineAnalyzer[IN_CHANNEL_COUNT];

    private AppCallback mAnalysisCallbackHandler;

    private DuplexAudioManager mDuplexAudioManager;
    int mCurrentPlayerMask;

    public SparseChannelAudioSourceProvider mSourceProvider;
    private SparseChannelAudioSource    mAudioSource;

    public AudioSinkProvider mSinkProvider =
            new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

    private TestManager mTestManager = new TestManager();
    private boolean mTestCanceled;

    class TestPhase {
        final int mOutputMask;      // 7.1, 5.1...
        final int mOutputChannel;   // The index of the channel to play (for the
                                    // SparseChannelAudioSourceProvider)

        // A description of the channel being "listened for".
        private final String mDescription;

        // Leave this null for all but the first phase for a new channel config
        String mHeading;

        boolean mPass;
        final int mPhaseIndex;
        private static int sNextPhaseIndex = 0;

        public double[] mMagnitude;
        public double[] mMaxMagnitude;
        public double[] mPhaseOffset;
        public double[] mPhaseJitter;

        public static final int STATUS_NOTRUN   = 0;
        public static final int STATUS_COMPLETE = 1;
        public int mState = STATUS_NOTRUN;

        TestPhase(int outMask, int outChannel, String description) {
            mOutputMask = outMask;
            mOutputChannel = outChannel;
            mDescription = description;
            mPhaseIndex = sNextPhaseIndex++;

            mMagnitude = new double[IN_CHANNEL_COUNT];
            mMaxMagnitude = new double[IN_CHANNEL_COUNT];
            mPhaseOffset = new double[IN_CHANNEL_COUNT];
            mPhaseJitter = new double[IN_CHANNEL_COUNT];
        }

        void clearState() {
            mState = STATUS_NOTRUN;

            for (int channelIndex = 0; channelIndex < IN_CHANNEL_COUNT; channelIndex++) {
                mMagnitude[channelIndex] = 0.0;
                mMaxMagnitude[channelIndex] = 0.0;
                mPhaseOffset[channelIndex] = 0.0;
                mPhaseJitter[channelIndex] = 0.0;
            }
        }

        void analyze() {
            mState = STATUS_COMPLETE;

            for (int channelIndex = 0; channelIndex < IN_CHANNEL_COUNT; channelIndex++) {
                mMagnitude[channelIndex] = mAnalyzers[channelIndex].getMagnitude();
                mMaxMagnitude[channelIndex] = mAnalyzers[channelIndex].getMaxMagnitude();
                mPhaseOffset[channelIndex] = mAnalyzers[channelIndex].getPhaseOffset();
                mPhaseJitter[channelIndex] = mAnalyzers[channelIndex].getPhaseJitter();
            }

            // Pass?
            mPass = hasSignal();
        }

        boolean hasSignal() {
            return (mMagnitude[IN_CHANNEL_LEFT] >= MIN_SIGNAL_PASS_MAGNITUDE
                        && mPhaseJitter[IN_CHANNEL_LEFT] < MAX_SIGNAL_PASS_JITTER)
                    || (mMagnitude[IN_CHANNEL_RIGHT] >= MIN_SIGNAL_PASS_MAGNITUDE
                        && mPhaseJitter[IN_CHANNEL_RIGHT] < MAX_SIGNAL_PASS_JITTER);
        }

        public int getPhaseIndex() {
            return this.mPhaseIndex;
        }

        String getSummary() {
            return "(" + getPhaseIndex() + ") " + mDescription
                + ", ch[" + mOutputChannel + "]";
        }

        private String megaAudioApiToString(int megaAudioApi) {
            return (megaAudioApi == BuilderBase.TYPE_JAVA) ? "Java" : "Native";
        }

        void logBeginning(int audioApi) {
            Log.d(TAG, "BEGIN_SUB_TEST: " + getSummary() + ", "
                    + megaAudioApiToString(mAudioApi));
        }

        void logEnding(int audioApi) {
            Log.d(TAG, "END_SUB_TEST: " + getSummary()
                    + ", " + megaAudioApiToString(mAudioApi)
                    + ", " + getPassFailString());
        }

        public String getPassFailString() {
            return mPass
                    ? getString(R.string.audio_general_pass)
                    : getString(R.string.audio_general_fail);
        }
    }

    class TestManager {
        static final String TAG = "TestManager";

        static final int TESTPHASE_NONE = -1;

        public static final int TESTSTATUS_NOT_RUN = 0;
        public static final int TESTSTATUS_RUNNING = 1;
        public static final int TESTSTATUS_COMPLETE = 2;
        public static final int TESTSTATUS_CANCELED = 3;
        public static final int TESTSTATUS_BADSTART = 4;

        public int mState = TESTSTATUS_NOT_RUN;

        private ArrayList<TestPhase> mTestModules = new ArrayList<TestPhase>();

        TestManager() {
        }

        void initializeTests() {
            TestPhase testPhase;

            String separatorStr = " - ";
            // AudioFormat.CHANNEL_OUT_STEREO
            // Just a basic functionality test.
            String stereoString = getString(R.string.audio_mixdown_stereo) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_STEREO, 0,
                    stereoString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_STEREO);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_STEREO, 1,
                    stereoString + getString(R.string.audio_channel_mask_right)));

            // AudioFormat.CHANNEL_OUT_QUAD
            String quadString = getString(R.string.audio_mixdown_quad) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 0,
                    quadString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_QUAD);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 1,
                    quadString + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 2,
                    quadString + getString(R.string.audio_channel_mask_left)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 3,
                    quadString + getString(R.string.audio_channel_mask_right)));

            // AudioFormat.CHANNEL_OUT_5POINT1
            String fiveDotOneString = getString(R.string.audio_mixdown_fivedotone) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 0,
                    fiveDotOneString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_5POINT1);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 1,
                    fiveDotOneString + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 2,
                    fiveDotOneString + getString(R.string.audio_channel_mask_center)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 3,
                    fiveDotOneString + getString(R.string.audio_channel_mask_lowfreq)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 4,
                    fiveDotOneString + getString(R.string.audio_channel_mask_backleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 5,
                    fiveDotOneString + getString(R.string.audio_channel_mask_backright)));

            // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            String sevenDotOneString = getString(R.string.audio_mixdown_sevendotone) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 0,
                    sevenDotOneString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_7POINT1_SURROUND);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 1,
                    sevenDotOneString + getString(R.string.audio_channel_mask_center)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 2,
                    sevenDotOneString + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 3,
                    sevenDotOneString + getString(R.string.audio_channel_mask_sideleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 4,
                    sevenDotOneString + getString(R.string.audio_channel_mask_sideright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 5,
                    sevenDotOneString + getString(R.string.audio_channel_mask_backleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 6,
                    sevenDotOneString + getString(R.string.audio_channel_mask_backright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 7,
                    sevenDotOneString + getString(R.string.audio_channel_mask_lowfreq)));

            // AudioFormat.CHANNEL_OUT_7POINT1POINT4
            String sevenDotOneDot4String = getString(R.string.audio_mixdown_sevendotonedotfour)
                    + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 0,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_7POINT1POINT4);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 1,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_center)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 2,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 3,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_sideleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 4,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_sideright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 5,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_backleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 6,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_backright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 7,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_lowfreq)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 8,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topfrontleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 9,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topfrontright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 10,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topbackleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 11,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topbackright)));
        }

        public int getNumTestPhases() {
            return mTestModules.size();
        }

        public TestPhase getTestPhase(int index) {
            return index != TestManager.TESTPHASE_NONE ? mTestModules.get(index) : null;
        }

        public void clearResults() {
            for (TestPhase testPhase : mTestModules) {
                testPhase.clearState();
            }
        }

        public void displayTestPhases() {
            if (!mIsHandheld) {
                return;
            }
            mWaveView.setVisibility(View.VISIBLE);
            mPhasesView.setVisibility(View.VISIBLE);
            mResultsView.setVisibility(View.GONE);
            StringBuilder sb = new StringBuilder();
            TestPhase currentTestPhase = getTestPhase(mTestPhase);
            for (TestPhase testPhase : mTestModules) {
                if (testPhase.mHeading != null) {
                    sb.append(testPhase.mHeading + "\n");
                }

                if (testPhase == currentTestPhase) {
                    sb.append(">> ");
                }

                sb.append(testPhase.getSummary());

                if (testPhase == currentTestPhase) {
                    sb.append(" <<");
                }
                sb.append("\n");
            }
            mPhasesView.setText(sb.toString());
        }

        public void displayTestResults() {
            mTextFormatter.clear();
            mTextFormatter.openDocument();

            Locale locale = Locale.getDefault();

            // "Bad Start"
            if (mTestManager.mState == TESTSTATUS_BADSTART) {
                mTextFormatter.openBold()
                    .appendText(getString(R.string.audio_mixdown_cantstart))
                        .closeBold();
            } else {
                // "Normal" Run
                if (mSelectedInput == null) {
                    mTextFormatter.openBold();
                    if (mSelectedInput == null) {
                        mTextFormatter.appendText(getString(R.string.audio_mixdown_invalidinput));
                        mTextFormatter.appendBreak();
                    }
                    mTextFormatter.appendText(getString(R.string.audio_mixdown_testcanceled));
                    mTextFormatter.closeBold();
                } else {
                    mTextFormatter.appendText(getString(R.string.audio_general_inputcolon)
                            + " " + mSelectedInput.getProductName());
                    mTextFormatter.appendBreak();
                }

                if (mSelectedOutput == null) {
                    mTextFormatter.openBold();
                    if (mSelectedOutput == null) {
                        mTextFormatter.appendText(getString(R.string.audio_mixdown_invalidoutput));
                        mTextFormatter.appendBreak();
                    }
                    mTextFormatter.appendText(getString(R.string.audio_mixdown_testcanceled));
                    mTextFormatter.closeBold();
                }
                else {
                    mTextFormatter.appendText(getString(R.string.audio_general_outputcolon)
                            + " " + mSelectedOutput.getProductName());
                    mTextFormatter.appendBreak();
                }

                for (TestPhase testPhase : mTestModules) {
                    if (testPhase.mState != TestPhase.STATUS_COMPLETE) {
                        mTextFormatter.openBold();
                        mTextFormatter.appendText(getString(R.string.audio_mixdown_testcanceled));
                        mTextFormatter.closeBold();
                        break;
                    }

                    if (testPhase.mHeading != null) {
                        mTextFormatter.openBold();
                        mTextFormatter.appendText(testPhase.mHeading);
                        mTextFormatter.closeBold();
                        mTextFormatter.appendBreak();
                    }

                    // Description
                    String separatorStr = " - ";
                    mTextFormatter.appendText(testPhase.getSummary() + separatorStr);
                    mTextFormatter.openBold();
                    mTextFormatter.appendText(testPhase.getPassFailString());
                    mTextFormatter.closeBold();
                    mTextFormatter.appendBreak();

                    mTextFormatter.openTextColor(testPhase.mPass ? "green" : "red");

                    // poor person's indent
                    mTextFormatter.appendText("  ");

                    // Left
                    String leftMagString =
                            String.format(locale, " mag:%.5f",
                                    testPhase.mMagnitude[IN_CHANNEL_LEFT]);
                    mTextFormatter.appendText(
                            getString(R.string.audio_general_left) + leftMagString + " ");

                    String leftJitterString = String.format(
                            locale, "jit:%.5f" , testPhase.mPhaseJitter[IN_CHANNEL_LEFT]);
                    mTextFormatter.appendText(leftJitterString);

                    mTextFormatter.appendBreak();
                    // poor person's indent
                    mTextFormatter.appendText("  ");

                    // Right
                    String rightMagString = String.format(
                            locale, " mag:%.5f", testPhase.mMagnitude[IN_CHANNEL_RIGHT]);
                    mTextFormatter.appendText(
                            getString(R.string.audio_general_right) + rightMagString);
                    String rightJitterString = String.format(
                            locale, " jit:%.5f" , testPhase.mPhaseJitter[IN_CHANNEL_RIGHT]);
                    mTextFormatter.appendText(rightJitterString);

                    mTextFormatter.closeTextColor();
                    mTextFormatter.appendBreak();
                }
            }

            // Pass Criteria
            String requiredString = " - " + getString(R.string.audio_mixdown_required);
            String notRequiredString = " - " + getString(R.string.audio_mixdown_notrequired);
            String completedString = " - " + getString(R.string.audio_mixdown_completed);
            String notCompletedString = " - " + getString(R.string.audio_mixdown_not_completed);

            // Speaker/Mic
            mTextFormatter.openParagraph();
            mTextFormatter.openBold();
            mTextFormatter.appendText(getString(R.string.audio_mixdown_micspeaker));
            mTextFormatter.appendText(mSpeakerMicRequired ? requiredString : notRequiredString);
            if (mSpeakerMicRequired) {
                mTextFormatter.appendText(mSpeakerMicRun ? completedString : notCompletedString);
            }
            mTextFormatter.closeBold();

            if (mSpeakerMicRequired && !mSpeakerMicRun) {
                // Ask them to run the Speaker/Mic path
                mTextFormatter.appendBreak()
                        .openItalic()
                        .appendText(getString(R.string.audio_mixdown_runspeakermic))
                        .closeItalic();
            }

            // Analog Headset
            mTextFormatter.openParagraph();
            mTextFormatter.openBold();
            mTextFormatter.appendText(getString(R.string.audio_mixdown_analogheadset));
            mTextFormatter.appendText(mAnalogJackRequired ? requiredString : notRequiredString);
            if (mAnalogJackRequired) {
                mTextFormatter.appendText(mAnalogJackRun ? completedString : notCompletedString);
            }
            mTextFormatter.closeBold();

            if (mAnalogJackRequired && !mAnalogJackRun) {
                // Ask them to run the Analog Headset path
                mTextFormatter.appendBreak()
                        .openItalic()
                        .appendText(getString(R.string.audio_mixdown_runanalogheadset))
                        .closeItalic();
            }

            // USB Interface
            mTextFormatter.openParagraph();
            mTextFormatter.openBold();
            mTextFormatter.appendText(getString(R.string.audio_mixdown_usbdevice));
            mTextFormatter.appendText(mUsbInterfaceRequired ? requiredString : notRequiredString);
            if (mUsbInterfaceRequired) {
                mTextFormatter.appendText(mUsbInterfaceRun ? completedString : notCompletedString);
            }
            mTextFormatter.closeBold();

            if (mUsbInterfaceRequired && !mUsbInterfaceRun) {
                // Ask them to run the USB Interface path
                mTextFormatter.appendBreak()
                        .openItalic()
                        .appendText(getString(R.string.audio_mixdown_runusbinterface))
                        .closeItalic();
            }

            // USB Headset
            mTextFormatter.openParagraph();
            mTextFormatter.openBold();
            mTextFormatter.appendText(getString(R.string.audio_mixdown_usbheadset));
            mTextFormatter.appendText(mUsbHeadsetRequired ? requiredString : notRequiredString);
            if (mUsbHeadsetRequired) {
                mTextFormatter.appendText(mUsbHeadsetRun ? completedString : notCompletedString);
            }
            mTextFormatter.closeBold();

            if (mUsbHeadsetRequired && !mUsbHeadsetRun) {
                // Ask them to run the USB Headset path
                mTextFormatter.appendBreak()
                        .openItalic()
                        .appendText(getString(R.string.audio_mixdown_runusbheadset))
                        .closeItalic();
            }

            // PASS message
            if (calculatePass()) {
                mTextFormatter.openParagraph();
                // Indicate the PASS state
                mTextFormatter.openBold();
                mTextFormatter.appendText("Test PASSES.");
                mTextFormatter.closeBold();
                mTextFormatter.appendBreak();

                // Instruct the user to press the PASS button
                mTextFormatter.appendText("Press the ");
                mTextFormatter.openBold();
                mTextFormatter.appendText("PASS");
                mTextFormatter.closeBold();
                mTextFormatter.appendText(" button below to complete the test.");
                mTextFormatter.closeParagraph();
            }

            mTextFormatter.closeDocument();
            mTextFormatter.put(mResultsView);

            showResultsView();
        }
    }

    void showResultsView() {
        mWaveView.setVisibility(View.GONE);
        mPhasesView.setVisibility(View.GONE);
        mResultsView.setVisibility(View.VISIBLE);
    }

    void setRouteButtonsText() {
        String requiredStr = getString(R.string.audio_mixdown_req);
        String doneStr = getString(R.string.audio_mixdown_done);

        if (mSpeakerMicRequired) {
            mSpeakerMicButton.setText(getString(R.string.audio_mixdown_micspeaker)
                    + " - " + (mSpeakerMicRun ? doneStr : requiredStr));
        }

        if (mAnalogJackRequired) {
            mAnalogJackButton.setText(getString(R.string.audio_mixdown_analogheadset)
                    + " - " + (mAnalogJackRun ? doneStr : requiredStr));
        }

        if (mUsbInterfaceRequired) {
            mUsbInterfaceButton.setText(getString(R.string.audio_mixdown_usbdevice)
                    + " - " + (mUsbInterfaceRun ? doneStr : requiredStr));
        }

        if (mUsbHeadsetRequired) {
            mUsbHeadsetButton.setText(getString(R.string.audio_mixdown_usbheadset)
                    + " - " + (mUsbHeadsetRun ? doneStr : requiredStr));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_multichannel_mixdown_activity);

        super.onCreate(savedInstanceState);

        mContext = this;

        mTestManager.initializeTests();

        // MegaAudio Initialization
        StreamBase.setup(this);

        mIsHandheld = AudioSystemFlags.isHandheld(this);

        mSpeakerMicRequired = mIsHandheld;

        mUsbInterfaceRequired = AudioDeviceUtils.supportsUsbAudioInterface(this)
                        == AudioDeviceUtils.SUPPORTSDEVICE_YES;
        mUsbHeadsetRequired = AudioDeviceUtils.supportsUsbAudio(this)
                        == AudioDeviceUtils.SUPPORTSDEVICE_YES;

        mAnalogJackRequired =
            AudioDeviceUtils.supportsAnalogHeadset(this) == AudioDeviceUtils.SUPPORTSDEVICE_YES;

        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_multichannel_mixdown_test,
                R.string.audio_multichannel_mixdown_info, -1);

        mStartButton = findViewById(R.id.audio_mixdown_start);
        mStartButton.setOnClickListener(this);
        mStartButton.setEnabled(mIsHandheld);

        mStopButton = findViewById(R.id.audio_mixdown_stop);
        mStopButton.setOnClickListener(this);
        mStopButton.setEnabled(false);

        mClearResultsButton = findViewById(R.id.audio_mixdown_clearresults);
        mClearResultsButton.setOnClickListener(this);

        mSpeakerMicButton = (RadioButton) findViewById(R.id.audio_mixdown_micspeaker);
        mSpeakerMicButton.setOnClickListener(this);

        mAnalogJackButton = (RadioButton) findViewById(R.id.audio_mixdown_analogheadset);
        mAnalogJackButton.setOnClickListener(this);

        mUsbInterfaceButton = (RadioButton) findViewById(R.id.audio_mixdown_usbdevice);
        mUsbInterfaceButton.setOnClickListener(this);

        mUsbHeadsetButton = (RadioButton) findViewById(R.id.audio_mixdown_usbheadset);
        mUsbHeadsetButton.setOnClickListener(this);

        setRouteButtonsText();

        mCalibrateAudioButton = findViewById(R.id.audio_mixdown_calibrate_button);
        mCalibrateAudioButton.setOnClickListener(this);

        mAudioDevicesButton = findViewById(R.id.audio_mixdown_devices_button);
        mAudioDevicesButton.setOnClickListener(this);

        mWaveView = (WaveScopeView) findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);
        mWaveView.setNumChannels(IN_CHANNEL_COUNT);

        mPhasesView = (TextView) findViewById(R.id.audio_mixdown_channels);

        mTextFormatter = AudioSystemFlags.supportsWebView(this)
                ? new HtmlFormatter() : new PlainTextFormatter();
        mResultsView = findViewById(R.id.audio_mixdown_results);

        mTestManager.displayTestPhases();

        mAudioManager = getSystemService(AudioManager.class);
        mConnectionListener = new AudioDeviceConnectionCallback();

        enableRouteButtons();

        // Analyzers
        //  - Left
        mAnalyzers[IN_CHANNEL_LEFT] = new BaseSineAnalyzer();
        mAnalyzers[IN_CHANNEL_LEFT].setInputChannel(IN_CHANNEL_LEFT);
        //  - Right
        mAnalyzers[IN_CHANNEL_RIGHT] = new BaseSineAnalyzer();
        mAnalyzers[IN_CHANNEL_RIGHT].setInputChannel(IN_CHANNEL_RIGHT);

        mAnalysisCallbackHandler = this;

        mSinkProvider = new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        DisplayUtils.setKeepScreenOn(this, true);

        getPassButton().setEnabled(!mIsHandheld);
        if (!mIsHandheld) {
            displayNonHandheldMessage();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mAudioManager.registerAudioDeviceCallback(mConnectionListener, null);
    }

    @Override
    public void onStop() {
        stopTest();
        mAudioManager.unregisterAudioDeviceCallback(mConnectionListener);
        super.onStop();
    }

    //
    // UI Helpers
    //
    void displayNonHandheldMessage() {
        mTextFormatter.clear();
        mTextFormatter.openDocument();
        mTextFormatter.openParagraph();
        mTextFormatter.appendText(getString(R.string.audio_exempt_nonhandheld));
        mTextFormatter.closeParagraph();

        mTextFormatter.closeDocument();
        mTextFormatter.put(mResultsView);
        showResultsView();
    }

    private void enableRouteButtons() {
        Log.i(TAG, "enableRouteButtons()");
        mSpeakerMicButton.setEnabled(mSpeakerOut != null && mMicrophoneIn != null);
        mAnalogJackButton.setEnabled(mAnalogHeadsetOut != null && mAnalogHeadsetIn != null);
        mUsbInterfaceButton.setEnabled(mUsbInterfaceOut != null && mUsbInterfaceIn != null);
        mUsbHeadsetButton.setEnabled(mUsbHeadsetOut != null && mUsbHeadsetIn != null);
    }

    private void setSelectedRouteButton() {
        if (mSelectedOutput == null) {
            return;
        }

        if (mSelectedOutput.equals(mSpeakerOut)) {
            selectRouteButton(mSpeakerMicButton.getId());
        } else if (mSelectedOutput.equals(mAnalogHeadsetOut)) {
            selectRouteButton(mAnalogJackButton.getId());
        } else if (mSelectedOutput.equals(mUsbHeadsetOut)) {
            selectRouteButton(mUsbHeadsetButton.getId());
        } else if (mSelectedOutput.equals(mUsbInterfaceOut)) {
            selectRouteButton(mUsbInterfaceButton.getId());
        }
    }

    //
    // Routes
    //
    // Handle "RadioButton" behavior.
    // Note: RadioGroup doesn't support multiple rows, so to get everything to fit w/o
    // taking up too much of the screen, do this ourselves
    //
    // Something based on this might be a better solution:
    // https://stackoverflow.com/questions/2961777/android-linearlayout-horizontal-with-wrapping-children
    private void selectRouteButton(int btnID) {
        // clear them...
        mSpeakerMicButton.setChecked(false);
        mAnalogJackButton.setChecked(false);
        mUsbInterfaceButton.setChecked(false);
        mUsbHeadsetButton.setChecked(false);
        // select "the one"
        ((RadioButton) findViewById(btnID)).setChecked(true);
    }

    //
    // Process
    //
    private boolean startTest() {
        if (mSelectedOutput == null || mSelectedInput == null) {
            stopTest();
            return false;
        }

        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);
        mClearResultsButton.setEnabled(false);
        mCalibrateAudioButton.setEnabled(false);
        mAudioDevicesButton.setEnabled(false);

        // setup for the first phase
        mTestManager.mState = TestManager.TESTSTATUS_RUNNING;

        mTestPhase = 0;
        mTestManager.displayTestPhases();

        // roll it back because advanceTestPhase() will increment this
        mTestPhase = TestManager.TESTPHASE_NONE;
        mTestCanceled = false;

        // This will force the duplex manager to restart
        mCurrentPlayerMask = 0;

        advanceTestPhase();

        return true;
    }

    private void completeTestPhase() {
        TestPhase testPhase = mTestManager.getTestPhase(mTestPhase);

        testPhase.analyze();

        mAnalyzers[IN_CHANNEL_LEFT].reset();
        mAnalyzers[IN_CHANNEL_RIGHT].reset();

        mWaveView.resetPersistentMaxMagnitude();
        testPhase.logEnding(mAudioApi);
    }

    private void advanceTestPhase() {
        if (mTestCanceled) {
            // Don't overwrite error code
            if (mTestManager.mState != TestManager.TESTSTATUS_BADSTART) {
                mTestManager.mState = TestManager.TESTSTATUS_CANCELED;
            }
            stopTest();
        } else if (mTestPhase == mTestManager.getNumTestPhases() - 1) {
            // we are done here
            mTestManager.mState = TestManager.TESTSTATUS_COMPLETE;
            // which route has been now tested
            if (mSelectedOutput.equals(mSpeakerOut)) {
                mSpeakerMicRun = true;
            } else if (mSelectedOutput.equals(mAnalogHeadsetOut)) {
                mAnalogJackRun = true;
            } else if (mSelectedOutput.equals(mUsbHeadsetOut)) {
                mUsbHeadsetRun = true;
            } else if (mSelectedOutput.equals(mUsbInterfaceOut)) {
                mUsbInterfaceRun = true;
            }

            stopTest();
        } else {
            mTestPhase++;

            TestPhase testPhase = mTestManager.getTestPhase(mTestPhase);
            testPhase.logBeginning(mAudioApi);
            float stateChangeDelay = 0.0f;
            if (mCurrentPlayerMask != testPhase.mOutputMask) {
                stopDuplex();
                startDuplex(testPhase);
                // add a delay to account for "settling"
                stateChangeDelay = 0.5f;
            }
            mTestManager.displayTestPhases();

            mAudioSource.setMask(1 << testPhase.mOutputChannel);

            (mTimer = new Timer()).schedule(new TimerTask() {
                @Override
                public void run() {
                    if (mTestPhase != TestManager.TESTPHASE_NONE) {
                        completeTestPhase();
                    }
                    advanceTestPhase();
                }
            }, (int) ((TEST_TIME_IN_SECONDS  + stateChangeDelay) * MS_PER_SEC));
        }
    }

    private void stopTest() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
            mTestPhase = TestManager.TESTPHASE_NONE;
            stopDuplex();
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartButton.setEnabled(true);
                mStopButton.setEnabled(false);
                mClearResultsButton.setEnabled(true);
                mCalibrateAudioButton.setEnabled(true);
                mAudioDevicesButton.setEnabled(true);

                mTestManager.displayTestResults();

                setRouteButtonsText();

                getPassButton().setEnabled(calculatePass());
            }
        });
    }

    private void startDuplex(TestPhase testPhase) {
        if (mDuplexAudioManager == null) {
            mDuplexAudioManager = new DuplexAudioManager(null, null);
        }

        if (mIsDuplexRunning) {
            mDuplexAudioManager.unwind();
        }

        mSourceProvider = new SparseChannelAudioSourceProvider(1 << testPhase.mOutputChannel);
        mDuplexAudioManager.setSources(mSourceProvider, mSinkProvider);

        // Player
        mDuplexAudioManager.setPlayerRouteDevice(mSelectedOutput);
        mDuplexAudioManager.setPlayerSampleRate(OUT_SAMPLE_RATE);
        mCurrentPlayerMask = testPhase.mOutputMask;
        mDuplexAudioManager.setPlayerChannelMask(mCurrentPlayerMask);

        // Recorder
        mDuplexAudioManager.setRecorderRouteDevice(mSelectedInput);
        mDuplexAudioManager.setRecorderSampleRate(IN_SAMPLE_RATE);
        mDuplexAudioManager.setNumRecorderChannels(IN_CHANNEL_COUNT);

        // Open the streams.
        // Note AudioSources and AudioSinks get allocated at this point
        int buildStatus = mDuplexAudioManager.buildStreams(mAudioApi, mAudioApi);
        if (buildStatus != DuplexAudioManager.DUPLEX_SUCCESS) {
            Log.e(TAG, "Couldn't Build Duplex Stream. buildStatus:0x"
                    + Integer.toHexString(buildStatus));
            // TODO - Provide more failure information here.
            mDuplexAudioManager.unwind();

            mTestManager.mState = TestManager.TESTSTATUS_BADSTART;
            mTestCanceled = true;
            mIsDuplexRunning = false;
        } else {
            // (potentially) Adjust AudioSource parameters
            mAudioSource = (SparseChannelAudioSource) mSourceProvider.getActiveSource();

            // Set the sample rate for the source (the sample rate for the player gets
            // set in the DuplexAudioManager.Builder.
            mAudioSource.setSampleRate(OUT_SAMPLE_RATE);
            mAudioSource.setMask(1 << testPhase.mOutputChannel);

            // Adjust the player frequency to match with the quantized frequency
            // of the analyzer.
            mAudioSource.setFreq((float) mAnalyzers[IN_CHANNEL_LEFT].getAdjustedFrequency());

            int startStatus = mDuplexAudioManager.start();
            if (startStatus != DuplexAudioManager.DUPLEX_SUCCESS) {
                Log.e(TAG, "Couldn't Start Duplex Stream. startStatus:0x"
                        + Integer.toHexString(startStatus));
                // TODO - Provide more failure information here.
                mDuplexAudioManager.unwind();

                mTestManager.mState = TestManager.TESTSTATUS_BADSTART;
                mTestCanceled = true;
                mIsDuplexRunning = false;
            } else {
                mIsDuplexRunning = true;
            }
        }
    }

    private void stopDuplex() {
        if (mIsDuplexRunning) {
            mDuplexAudioManager.unwind();
            mIsDuplexRunning = false;
        }
    }

    private boolean calculatePass() {
        // Pass criteria
        // For now just grant a pass if all have been run,
        // but ultimately we want them to pass all paths
        boolean pass =
                (!mSpeakerMicRequired || mSpeakerMicRun)
                && (!mAnalogJackRequired || mAnalogJackRun)
                && (!mUsbInterfaceRequired || mUsbInterfaceRun)
                && (!mUsbHeadsetRequired || mUsbHeadsetRun);

        return pass;
    }

    //
    // AudioMultiApiActivity Overrides
    //
    @Override
    public void onApiChange(int api) {
        mTestManager.displayTestPhases();
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_mixdown_start) {
            startTest();
        } else if (id == R.id.audio_mixdown_stop) {
            stopTest();
        } else if (id == R.id.audio_mixdown_clearresults) {
            mTestManager.clearResults();
            mTestManager.displayTestPhases();
        } else if (id == R.id.audioJavaApiBtn || id == R.id.audioNativeApiBtn) {
            super.onClick(view);
        } else if (id == R.id.audio_mixdown_calibrate_button) {
            (new AudioLoopbackCalibrationDialog(this)).show();
        } else if (id == R.id.audio_mixdown_devices_button) {
            (new AudioDevicesDialog(this)).show();
        } else if (id == R.id.audio_mixdown_micspeaker) {
            selectRouteButton(id);
            mSelectedOutput = mSpeakerOut;
            mSelectedInput = mMicrophoneIn;
        } else if (id == R.id.audio_mixdown_analogheadset) {
            selectRouteButton(id);
            mSelectedOutput = mAnalogHeadsetOut;
            mSelectedInput = mAnalogHeadsetIn;
        } else if (id == R.id.audio_mixdown_usbdevice) {
            selectRouteButton(id);
            mSelectedOutput = mUsbInterfaceOut;
            mSelectedInput = mUsbInterfaceIn;
        } else if (id == R.id.audio_mixdown_usbheadset) {
            selectRouteButton(id);
            mSelectedOutput = mUsbHeadsetOut;
            mSelectedInput = mUsbHeadsetIn;
        }
    }

    //
    // (MegaAudio) AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        mAnalyzers[IN_CHANNEL_LEFT].analyzeBuffer(audioData, IN_CHANNEL_COUNT, numFrames);
        mAnalyzers[IN_CHANNEL_RIGHT].analyzeBuffer(audioData, IN_CHANNEL_COUNT, numFrames);
        mWaveView.setPCMFloatBuff(audioData, IN_CHANNEL_COUNT, numFrames);
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        private void clearRoutes() {
            // Routes
            mUsbInterfaceOut =
                mUsbInterfaceIn =
                mUsbHeadsetOut =
                mUsbHeadsetIn =
                mAnalogHeadsetOut =
                mAnalogHeadsetIn =
                mSpeakerOut =
                mMicrophoneIn = null;

            // Selection
            mSelectedOutput = mSelectedInput = null;
        }

        void selectInputDevice(AudioDeviceInfo devInfo) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                mSelectedInput = mAnalogHeadsetIn = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mSelectedInput = mUsbInterfaceIn = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                mSelectedInput = mUsbHeadsetIn = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                mSelectedInput = mMicrophoneIn = devInfo;
            }
        }

        void selectOutputDevice(AudioDeviceInfo devInfo) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                mSelectedOutput = mAnalogHeadsetOut = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mSelectedOutput = mUsbInterfaceOut = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                mSelectedOutput = mUsbHeadsetOut = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                mSelectedOutput = mSpeakerOut = devInfo;
            }
        }

        void recalcIODevices() {
            clearRoutes();

            AudioDeviceInfo[] inputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo devInfo : inputDevices) {
                selectInputDevice(devInfo);
            }

            AudioDeviceInfo[] outputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo devInfo : outputDevices) {
                selectOutputDevice(devInfo);
            }

            enableRouteButtons();
            setSelectedRouteButton();
        }

        /**
         * @param addedDevices from onAudioDevicesAdded() callback.
         * @return true if a useable pair of I/O devices is connected
         */
        boolean addDeviceHandler(AudioDeviceInfo[] addedDevices) {
            for (AudioDeviceInfo devInfo : addedDevices) {
                if (devInfo.isSource()) {
                    selectInputDevice(devInfo);
                } else {
                    selectOutputDevice(devInfo);
                }
            }

            enableRouteButtons();
            setSelectedRouteButton();
            return true;
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (addedDevices == null) {
                return;
            }
            if (addDeviceHandler(addedDevices)) {
                mTestManager.displayTestPhases();
            }
            if (addedDevices[0].getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                AudioDeviceUtils.validateUsbDevice(mContext);
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            recalcIODevices();
        }
    }
}
