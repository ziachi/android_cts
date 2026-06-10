/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.mediastress.cts;

import android.media.MediaPlayer;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Junit / Instrumentation test case for the media player api
 */
public class CodecTest {
    private static String TAG = "CodecTest";
    private static MediaPlayer mMediaPlayer;

    private static int WAIT_FOR_COMMAND_TO_COMPLETE = 60000;  //1 min max.
    private static Looper mLooper = null;
    private static final Object mLock = new Object();
    private static final int PLAYBACK_SETTLE_TIME_MS = 5000;
    private static final int SETUP_SETTLE_TIME_MS = 5000;

    public static CountDownLatch mFirstFrameLatch;
    public static CountDownLatch mCompletionLatch;
    public static boolean mOnCompleteSuccess = false;
    public static boolean mPlaybackError = false;

    public static int mMediaInfoUnknownCount = 0;
    public static int mMediaInfoVideoTrackLaggingCount = 0;
    public static int mMediaInfoBadInterleavingCount = 0;
    public static int mMediaInfoNotSeekableCount = 0;
    public static int mMediaInfoMetdataUpdateCount = 0;


    /*
     * Initializes the message looper so that the mediaPlayer object can
     * receive the callback messages.
     */
    private static void initializeMessageLooper() {
        Log.v(TAG, "start looper");
        new Thread() {
            @Override
            public void run() {
                // Set up a looper to be used by camera.
                Looper.prepare();
                Log.v(TAG, "start loopRun");
                // Save the looper so that we can terminate this thread
                // after we are done with it.
                mLooper = Looper.myLooper();
                mMediaPlayer = new MediaPlayer();
                synchronized (mLock) {
                    mLock.notify();
                }
                Looper.loop();  // Blocks forever until Looper.quit() is called.
                Log.v(TAG, "initializeMessageLooper: quit.");
            }
        }.start();
    }

    /*
     * Terminates the message looper thread.
     */
    private static void terminateMessageLooper() {
        if (mLooper != null) {
            mLooper.quit();
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
    }

    private static MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    Log.v(TAG, "notify the completion callback");
                    mOnCompleteSuccess = true;
                    mCompletionLatch.countDown();
                }
            };

    private static MediaPlayer.OnErrorListener mOnErrorListener =
            new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
                    Log.v(TAG, "playback error");
                    mPlaybackError = true;
                    mp.reset();
                    mOnCompleteSuccess = false;
                    mCompletionLatch.countDown();
                    return true;
                }
            };

    private static MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_UNKNOWN:
                    mMediaInfoUnknownCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                    mMediaInfoVideoTrackLaggingCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                    mMediaInfoBadInterleavingCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                    mMediaInfoNotSeekableCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    mMediaInfoMetdataUpdateCount++;
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    mFirstFrameLatch.countDown();
                    break;
            }
            return true;
        }
    };

    private static void setupPlaybackMarkers() {
        // we only ever worry about these firing 1 time
        mFirstFrameLatch = new CountDownLatch(1);
        mCompletionLatch = new CountDownLatch(1);

        mOnCompleteSuccess = false;
        mPlaybackError = false;

        mMediaInfoUnknownCount = 0;
        mMediaInfoVideoTrackLaggingCount = 0;
        mMediaInfoBadInterleavingCount = 0;
        mMediaInfoNotSeekableCount = 0;
        mMediaInfoMetdataUpdateCount = 0;
    }

    // null == success, !null == reason why it failed
    public static String playMediaSample(String fileName) throws Exception {
        int duration = 0;

        setupPlaybackMarkers();

        initializeMessageLooper();
        synchronized (mLock) {
            try {
                mLock.wait(WAIT_FOR_COMMAND_TO_COMPLETE);
            } catch(Exception e) {
                Log.v(TAG, "looper was interrupted.");
                return "Looper was interrupted";
            }
        }
        try {
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mOnErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            Log.v(TAG, "playMediaSample: sample file name " + fileName);
            mMediaPlayer.setDataSource(fileName);
            mMediaPlayer.setDisplay(MediaFrameworkTest.getSurfaceView().getHolder());
            mMediaPlayer.prepare();
            duration = mMediaPlayer.getDuration();
            Log.v(TAG, "duration of media " + duration);

            // start to play
            long time_started = SystemClock.uptimeMillis();
            long time_firstFrame = time_started - 1;
            long time_completed = time_started - 1;
            mMediaPlayer.start();

            boolean happyStart = mFirstFrameLatch.await(SETUP_SETTLE_TIME_MS,
                            TimeUnit.MILLISECONDS);
            time_firstFrame = SystemClock.uptimeMillis();
            if (happyStart == false) {
                String msg = "playMediaSamples playback did not start within "
                                + SETUP_SETTLE_TIME_MS + " ms";
                Log.i(TAG, msg);
                return msg;
            }

            // now that we know playback has started, calculate when it should
            // finish and wait that long that. Account for what has already
            // played (should be very close to 0 as we get here shortly after playback
            // starts)
            int startingPosition = mMediaPlayer.getCurrentPosition();
            int remainingDuration = duration - startingPosition;

            boolean happyFinish = mCompletionLatch.await(remainingDuration + PLAYBACK_SETTLE_TIME_MS,
                            TimeUnit.MILLISECONDS);
            time_completed = SystemClock.uptimeMillis();

            // really helps diagnose the class of failures we've seen.
            if (true) {
                Log.i(TAG, "duration of video sample:             " + duration + " ms");
                Log.i(TAG, "full start+playback+completionsignal: "
                                + (time_completed - time_started) + " ms");
                Log.i(TAG, "total overhead:                       "
                                + (time_completed - time_started - duration) + " ms");
                Log.i(TAG, "time until 1st frame rendered:        "
                                + (time_firstFrame - time_started) + " ms");
                Log.i(TAG, "video position when started timer:    " + startingPosition + " ms");
                long preOverhead = (time_firstFrame - time_started) - (startingPosition);
                Log.i(TAG, "start() startup overhead:             " + preOverhead + " ms");
                long postOverhead = (time_completed - time_started) - duration - preOverhead;
                Log.i(TAG, "trailing costs overhead:              " + postOverhead + " ms");
            }

            // did we succeed?
            if (happyFinish == false) {
                // the test failed

                // wait a little more, to help who is trying to figure out why it's bad.
                boolean happyExtra = mCompletionLatch.await(10000, TimeUnit.MILLISECONDS);
                long time_extension = SystemClock.uptimeMillis();

                String extraTime = "";
                if (happyExtra) {
                    extraTime = " BUT complete after an additional "
                                    + (time_extension - time_completed) + " ms";
                } else {
                    extraTime = " AND still not complete after an additional "
                                    + (time_extension - time_completed) + " ms";
                }

                // it's still a failure, even if we did finish in extra time
                Log.e(TAG, "wait timed-out without onCompletion notification" + extraTime);
                return "wait timed-out without onCompletion notification" + extraTime;
            }
        } catch (Exception e) {
            Log.v(TAG, "playMediaSample Exception:" + e.getMessage());
        } finally {
            // we need to clean up, even if we tripped an early return above
            terminateMessageLooper();
        }
        return mOnCompleteSuccess ? null : "unknown failure reason";
    }
}
