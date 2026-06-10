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

package com.android.interactive;

import static com.android.interactive.testrules.TestNameSaver.INTERACTIVE_TEST_NAME;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;

import java.io.File;
import java.time.Duration;

/**
 * Utility class for taking screenshots during xTS-Interactive tests.
 *
 * <p>It saves screenshots as files to the /Documents/xts/screenshots/ directory on the external
 * storage.
 */
public final class ScreenshotUtil {

    private static final String LOG_TAG = "Interactive.Screenshot";

    /**
     * A {@link Runnable} that takes a screenshot and marks the finish status after the completion.
     */
    private static final class ScreenshotTaker implements Runnable {

        private final String mScreenshotName;
        private boolean mFinished = false;

        ScreenshotTaker(String screenshotName) {
            mScreenshotName = screenshotName;
        }

        @Override
        public void run() {
            ScreenshotUtil.captureScreenshot(
                    mScreenshotName, /* withTestName= */ false, /* withSystemTime= */ true);
            mFinished = true;
        }

        /** Whether the screenshot has been taken. */
        boolean isFinished() {
            return mFinished;
        }
    }

    /** Default timeout to wait for a screenshot to be taken. */
    private static final Duration MAX_SCREENSHOT_DURATION = Duration.ofSeconds(5);

    /**
     * Captures a screenshot and saves it as a file.
     *
     * <p>The name of the current test will be appended as a prefix of the screenshot. And the
     * current system time will be appended as a suffix of the screenshot.
     *
     * @param screenshotName the screenshot name
     */
    public static void captureScreenshot(String screenshotName) {
        captureScreenshot(screenshotName, /* withTestName= */ true, /* withSystemTime= */ true);
    }

    /**
     * Captures a screenshot and saves it as a file.
     *
     * @param screenshotName the screenshot name
     * @param withTestName whether adding the name of the current test as the prefix of the
     *     screenshot
     * @param withSystemTime whether adding the current system time as the suffix of the screenshot
     */
    public static void captureScreenshot(
            String screenshotName, boolean withTestName, boolean withSystemTime) {
        String screenshotDir =
                Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/Documents/xts/screenshots/";
        File file = new File(screenshotDir);
        if (!file.exists() && !file.mkdirs()) {
            // Let the steps that require screenshots fail immediately.
            throw new RuntimeException("Failed to create " + screenshotDir + " directory on DUT.");
        }

        String screenshotFileName =
                withTestName ? getTestName() + "__" + screenshotName : screenshotName;
        screenshotFileName =
                withSystemTime
                        ? screenshotFileName + "__" + System.currentTimeMillis()
                        : screenshotFileName;

        File screenshotFile = new File(screenshotDir, screenshotFileName + ".png");
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(screenshotFile);

        Log.i(LOG_TAG, "Screenshot " + screenshotFileName + " is taken.");
    }

    /**
     * Captures a screenshot and saves it as a file with a delay in milli-seconds.
     *
     * <p>The screenshot is taken by calling {@link #captureScreenshot(String, boolean, boolean)}
     * with the given screenshotName, withTestName (false), withSystemTime(true) in another thread.
     * The withTestName is set to false as when the screenshot is taken, the original test name of
     * the context may have been removed or overridden.
     *
     * <p>The main thread (the caller) will be blocked to wait for the screenshot to be taken
     * successfully.
     *
     * <p>If it fails to take a screenshot or timeouts by {@link #MAX_SCREENSHOT_DURATION}, a
     * runtime exception is thrown.
     *
     * @param screenshotName the screenshot name
     * @param delayInMillis the delay in milli-seconds to take the screenshot
     */
    public static void captureScreenshotWithDelay(String screenshotName, Long delayInMillis) {
        HandlerThread handlerThread = new HandlerThread("ScreenshotThread");
        handlerThread.start();

        ScreenshotTaker screenshotTaker = new ScreenshotTaker(screenshotName);
        new Handler(handlerThread.getLooper()).postDelayed(screenshotTaker, delayInMillis);
        Poll.forValue("screenshotFinished", screenshotTaker::isFinished)
                .toBeEqualTo(true)
                .timeout(MAX_SCREENSHOT_DURATION)
                .await();

        handlerThread.quit();
    }

    /**
     * Gets the test name stored within a test execution. Returns an empty string if it's not set.
     */
    private static String getTestName() {
        return TestApis.context()
                .instrumentedContext()
                .getSharedPreferences(INTERACTIVE_TEST_NAME, Context.MODE_PRIVATE)
                .getString(INTERACTIVE_TEST_NAME, "");
    }
}
